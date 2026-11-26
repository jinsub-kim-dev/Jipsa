#!/usr/bin/env node
/**
 * 데모 시드 SQL 생성기.
 *
 * UI 프로토타입의 더미데이터(ui-prototypes/data.js)를 Flyway 시드 마이그레이션으로 옮긴다.
 * 화면 후보를 고를 때 본 데이터와 실제 앱이 보여주는 데이터를 같게 유지하기 위함이다.
 *
 *   node scripts/generate-seed.mjs
 *   -> backend/src/main/resources/db/seed/V100__seed_demo_data.sql
 *
 * 시드는 dev 프로필에만 적용된다 (CLAUDE.md 2.1). 할부는 도메인 규칙(PRD 3.3)과 같게
 * 모든 회차를 미리 만든다. 회차 금액 합계 == 총액 은 JipsaApplicationTests 가 검증한다.
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const sandbox = { window: {} }
vm.runInNewContext(readFileSync(resolve(root, 'ui-prototypes/data.js'), 'utf8'), sandbox)
const L = sandbox.window.LEDGER

const q = (value) => (value === null || value === undefined ? 'NULL' : `'${String(value).replaceAll("'", "''")}'`)
const now = 'NOW(6)'

// --- 카테고리 ---
// 카테고리는 모든 환경에 필요한 기본값이라 스키마 마이그레이션(V4)이 넣는다. 시드는 그 식별자를 참조만 한다.
// 두 목록이 어긋나면 거래가 엉뚱한 카테고리를 가리키므로 생성 전에 검사한다.
const categoryIds = new Map(Object.keys(L.CATEGORIES).map((name, index) => [name, index + 1]))
const migration = readFileSync(resolve(root, 'backend/src/main/resources/db/migration/V4__insert_default_categories.sql'), 'utf8')
const migrated = [...migration.matchAll(/\((\d+), '([^']+)'/g)].map(([, id, name]) => [Number(id), name])
for (const [name, id] of categoryIds) {
  if (!migrated.some(([mid, mname]) => mid === id && mname === name)) {
    throw new Error(`V4 기본 카테고리와 프로토타입 카테고리가 다릅니다: ${id} ${name}`)
  }
}

// --- 고정비 ---
const firstMonth = L.MONTHS[0]
const fixedIds = new Map(L.FIXED.map((f, index) => [f.merchant, index + 1]))
const fixedRows = L.FIXED.map((f) => {
  // 관리비처럼 범위로 정의된 고정비는 중간값을 정의 금액으로 둔다. 달마다 실제 금액은 기록된 거래에 있다.
  const amount = Array.isArray(f.amount) ? Math.round((f.amount[0] + f.amount[1]) / 2 / 1000) * 1000 : f.amount
  return `(${fixedIds.get(f.merchant)}, ${categoryIds.get(f.category)}, ${q(f.merchant)}, ${amount}, ${f.day}, '신한카드', '${firstMonth}-01', NULL, ${now}, ${now})`
})
const fixedTagRows = L.FIXED.flatMap((f) => f.tags.map((tag, position) => `(${fixedIds.get(f.merchant)}, ${position}, ${q(tag)})`))

// --- 할부 계획 ---
const planIds = new Map(L.INSTALLMENTS.map((p, index) => [p.id, index + 1]))
const planRows = L.INSTALLMENTS.map(
  (p) =>
    `(${planIds.get(p.id)}, ${categoryIds.get(p.category)}, ${q(p.merchant)}, ${p.principal}, ${p.months}, '${p.start}-01', ${p.day}, ${q(p.card)}, ${now}, ${now})`,
)
const planTagRows = L.INSTALLMENTS.flatMap((p) => p.tags.map((tag, position) => `(${planIds.get(p.id)}, ${position}, ${q(tag)})`))

// --- 거래 ---
const transactions = []

// 일반 지출과 고정비 기록은 프로토타입 데이터 그대로 쓴다 (관리비의 달별 금액 포함).
for (const t of [...L.TX].reverse()) {
  if (t.kind === 'installment') continue
  transactions.push({
    kind: t.kind === 'fixed' ? 'FIXED' : 'GENERAL',
    categoryId: categoryIds.get(t.category),
    amount: t.amount,
    merchant: t.merchant,
    spentDate: t.date,
    memo: t.memo || null,
    paymentMethod: t.card,
    fixedExpenseId: t.kind === 'fixed' ? fixedIds.get(t.merchant) : null,
    installmentPlanId: null,
    installmentSequence: null,
    tags: t.tags,
  })
}

// 할부 회차는 도메인 규칙대로 모든 회차를 만든다. 몫은 내림, 나머지는 1회차에 더한다.
const pad = (n) => String(n).padStart(2, '0')
for (const p of L.INSTALLMENTS) {
  const quotient = Math.floor(p.principal / p.months)
  const remainder = p.principal - quotient * p.months
  for (let sequence = 1; sequence <= p.months; sequence++) {
    const month = L.addMonths(p.start, sequence - 1)
    const day = Math.min(p.day, L.daysIn(month))
    transactions.push({
      kind: 'INSTALLMENT',
      categoryId: categoryIds.get(p.category),
      amount: sequence === 1 ? quotient + remainder : quotient,
      merchant: p.merchant,
      spentDate: `${month}-${pad(day)}`,
      memo: null,
      paymentMethod: p.card,
      fixedExpenseId: null,
      installmentPlanId: planIds.get(p.id),
      installmentSequence: sequence,
      tags: p.tags,
    })
  }
}

transactions.sort((a, b) => a.spentDate.localeCompare(b.spentDate))
transactions.forEach((t, index) => (t.id = index + 1))

const transactionRows = transactions.map(
  (t) =>
    `(${t.id}, '${t.kind}', ${t.categoryId}, ${t.amount}, ${q(t.merchant)}, '${t.spentDate}', ${q(t.memo)}, ${q(t.paymentMethod)}, ${t.fixedExpenseId ?? 'NULL'}, ${t.installmentPlanId ?? 'NULL'}, ${t.installmentSequence ?? 'NULL'}, ${now}, ${now})`,
)
const transactionTagRows = transactions.flatMap((t) => t.tags.map((tag, position) => `(${t.id}, ${position}, ${q(tag)})`))

const chunk = (rows, size = 200) =>
  Array.from({ length: Math.ceil(rows.length / size) }, (_, i) => rows.slice(i * size, (i + 1) * size))

const insert = (table, columns, rows) =>
  chunk(rows)
    .map((part) => `INSERT INTO ${table} (${columns})\nVALUES ${part.join(',\n       ')};`)
    .join('\n\n')

const sql = `-- 데모 시드 데이터 (dev 프로필 전용)
--
-- 이 파일은 생성된다. 직접 고치지 말고 scripts/generate-seed.mjs 를 고친 뒤 다시 생성한다.
--   node scripts/generate-seed.mjs
--
-- 원본: ui-prototypes/data.js (${L.MONTHS[0]} ~ ${L.MONTHS[L.MONTHS.length - 1]}, 기준일 ${L.TODAY})
-- 카테고리 ${categoryIds.size}(V4 마이그레이션) · 고정비 ${fixedRows.length} · 할부 ${planRows.length} · 거래 ${transactionRows.length}

${insert('fixed_expense', 'id, category_id, merchant, amount, day_of_month, payment_method, start_month, end_month, created_at, updated_at', fixedRows)}

${insert('fixed_expense_tag', 'fixed_expense_id, position, name', fixedTagRows)}

${insert('installment_plan', 'id, category_id, merchant, total_amount, installment_months, first_billing_month, billing_day, payment_method, created_at, updated_at', planRows)}

${insert('installment_plan_tag', 'installment_plan_id, position, name', planTagRows)}

${insert('transaction', 'id, kind, category_id, amount, merchant, spent_date, memo, payment_method, fixed_expense_id, installment_plan_id, installment_sequence, created_at, updated_at', transactionRows)}

${insert('transaction_tag', 'transaction_id, position, name', transactionTagRows)}
`

const out = resolve(root, 'backend/src/main/resources/db/seed/V100__seed_demo_data.sql')
writeFileSync(out, sql)
console.log(`생성: ${out}`)
console.log(`카테고리 ${categoryIds.size}(V4) · 고정비 ${fixedRows.length} · 할부 ${planRows.length} · 거래 ${transactionRows.length}`)
