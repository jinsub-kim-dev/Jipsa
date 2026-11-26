/* 가계부 UI 후보 공통 더미데이터
 * - 2026-06 ~ 2026-11 (6개월), 오늘 = 2026-11-26
 * - 지출 종류(kind): general(일반 지출) / fixed(고정비) / installment(할부)
 * - 카테고리(category, 단일) + 태그(tags, 복수)
 * - 시드 고정 난수라 새로고침해도 같은 데이터가 나온다.
 */
(function () {
  function mulberry32(a) {
    return function () {
      a |= 0; a = (a + 0x6d2b79f5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  const rnd = mulberry32(20261126);
  const randInt = (a, b) => a + Math.floor(rnd() * (b - a + 1));
  const pick = (arr) => arr[Math.floor(rnd() * arr.length)];
  const roundTo = (v, u = 100) => Math.round(v / u) * u;

  const TODAY = '2026-11-26';
  const MONTHS = ['2026-06', '2026-07', '2026-08', '2026-09', '2026-10', '2026-11'];

  const KINDS = {
    general: { label: '일반 지출', short: '일반', color: '#3B82F6' },
    fixed: { label: '고정비', short: '고정', color: '#F59E0B' },
    installment: { label: '할부', short: '할부', color: '#8B5CF6' },
  };

  const CATEGORIES = {
    '식비': { icon: '🍚', color: '#EF4444' },
    '카페·간식': { icon: '☕', color: '#B45309' },
    '교통': { icon: '🚇', color: '#0EA5E9' },
    '쇼핑': { icon: '🛍️', color: '#EC4899' },
    '문화·여가': { icon: '🎬', color: '#10B981' },
    '생활': { icon: '🧴', color: '#64748B' },
    '주거·통신': { icon: '🏠', color: '#F59E0B' },
    '구독': { icon: '📺', color: '#6366F1' },
    '보험·건강': { icon: '💊', color: '#14B8A6' },
    '경조사·선물': { icon: '🎁', color: '#F97316' },
    '전자기기': { icon: '💻', color: '#8B5CF6' },
    '여행': { icon: '✈️', color: '#06B6D4' },
  };

  const CARDS = ['신한카드', '현대카드', '토스뱅크 체크'];

  const FIXED = [
    { merchant: '월세', amount: 650000, day: 1, category: '주거·통신', tags: ['집'] },
    { merchant: '관리비', amount: [110000, 160000], day: 25, category: '주거·통신', tags: ['집'] },
    { merchant: '통신요금', amount: 69000, day: 20, category: '주거·통신', tags: ['통신'] },
    { merchant: '넷플릭스', amount: 17000, day: 5, category: '구독', tags: ['OTT'] },
    { merchant: '유튜브 프리미엄', amount: 14900, day: 12, category: '구독', tags: ['OTT'] },
    { merchant: 'ChatGPT Plus', amount: 29000, day: 18, category: '구독', tags: ['업무툴'] },
    { merchant: '실손보험', amount: 48000, day: 10, category: '보험·건강', tags: ['보험'] },
    { merchant: '헬스장', amount: 80000, day: 3, category: '보험·건강', tags: ['운동'] },
  ];

  const INSTALLMENTS = [
    { id: 'ins1', merchant: '맥북 프로 14', principal: 3096000, monthly: 258000, months: 12, start: '2026-03', day: 15, category: '전자기기', tags: ['전자기기', '업무'], card: '현대카드' },
    { id: 'ins2', merchant: '양문형 냉장고', principal: 2280000, monthly: 95000, months: 24, start: '2025-12', day: 15, category: '생활', tags: ['가전'], card: '신한카드' },
    { id: 'ins3', merchant: '제주 항공권·숙소', principal: 558000, monthly: 186000, months: 3, start: '2026-09', day: 15, category: '여행', tags: ['여행', '휴가'], card: '신한카드' },
    { id: 'ins4', merchant: '겨울 패딩', principal: 288000, monthly: 96000, months: 3, start: '2026-11', day: 15, category: '쇼핑', tags: ['의류', '블프'], card: '현대카드' },
  ];

  // n: 월 발생 건수 범위, boost: 특정 월 건수 배수
  const GENERAL = [
    { category: '식비', merchants: ['김밥천국', '본죽', '서브웨이', '국밥집', '샐러디'], range: [8000, 14000], tags: [['점심'], ['점심'], ['점심', '혼밥']], n: [14, 19] },
    { category: '식비', merchants: ['배달의민족', '쿠팡이츠'], range: [18000, 36000], tags: [['배달'], ['배달', '야식']], n: [4, 8], boost: { '2026-10': 1.6, '2026-11': 1.4 } },
    { category: '식비', merchants: ['이마트', '홈플러스', '마켓컬리'], range: [25000, 90000], tags: [['장보기']], n: [2, 4] },
    { category: '식비', merchants: ['고깃집', '이자카야', '파스타집'], range: [30000, 85000], tags: [['외식', '데이트'], ['회식'], ['외식', '친구']], n: [2, 5], boost: { '2026-07': 1.5 } },
    { category: '카페·간식', merchants: ['스타벅스', '메가커피', '투썸플레이스', '편의점'], range: [2000, 7500], tags: [['커피'], ['커피'], ['편의점']], n: [16, 24] },
    { category: '교통', merchants: ['교통카드 충전'], range: [50000, 50000], tags: [['대중교통']], n: [2, 2] },
    { category: '교통', merchants: ['카카오T 택시'], range: [7000, 26000], tags: [['택시'], ['택시', '야근']], n: [2, 6], boost: { '2026-10': 1.8 } },
    { category: '쇼핑', merchants: ['쿠팡', '무신사', '올리브영', '29CM'], range: [15000, 120000], tags: [['온라인'], ['의류'], ['뷰티'], ['온라인', '충동구매']], n: [2, 5], boost: { '2026-11': 2.2 } },
    { category: '문화·여가', merchants: ['CGV', '교보문고', '전시회', '볼링장'], range: [12000, 45000], tags: [['영화', '주말'], ['책'], ['전시', '주말'], ['친구', '주말']], n: [1, 4], boost: { '2026-08': 1.8 } },
    { category: '생활', merchants: ['다이소', '세탁소', '생활용품점'], range: [3000, 30000], tags: [['생필품']], n: [2, 5] },
    { category: '보험·건강', merchants: ['약국', '이비인후과', '치과'], range: [4000, 45000], tags: [['병원']], n: [0, 2], boost: { '2026-11': 2 } },
    { category: '경조사·선물', merchants: ['축의금', '카카오 선물하기'], range: [30000, 100000], tags: [['경조사'], ['선물']], n: [0, 2] },
  ];

  const EVENTS = [
    { date: '2026-08-02', merchant: '강릉 숙소', amount: 286000, category: '여행', tags: ['여행', '휴가'] },
    { date: '2026-08-03', merchant: '강릉 횟집', amount: 94000, category: '식비', tags: ['여행', '외식'] },
    { date: '2026-08-04', merchant: 'KTX', amount: 59600, category: '교통', tags: ['여행'] },
    { date: '2026-09-22', merchant: '추석 선물세트', amount: 89000, category: '경조사·선물', tags: ['명절', '선물'] },
    { date: '2026-09-23', merchant: '추석 선물세트', amount: 89000, category: '경조사·선물', tags: ['명절', '선물'] },
    { date: '2026-09-25', merchant: '부모님 용돈', amount: 300000, category: '경조사·선물', tags: ['명절', '가족'] },
    { date: '2026-11-21', merchant: '무신사 블랙프라이데이', amount: 164000, category: '쇼핑', tags: ['의류', '블프', '충동구매'] },
    { date: '2026-11-22', merchant: '쿠팡 블프 특가', amount: 72000, category: '쇼핑', tags: ['온라인', '블프'] },
  ];

  const pad = (n) => String(n).padStart(2, '0');
  const daysIn = (m) => { const [y, mo] = m.split('-').map(Number); return new Date(y, mo, 0).getDate(); };
  const monthDiff = (a, b) => { const [ay, am] = a.split('-').map(Number); const [by, bm] = b.split('-').map(Number); return (by - ay) * 12 + (bm - am); };
  const addMonths = (m, k) => { const [y, mo] = m.split('-').map(Number); const d = new Date(y, mo - 1 + k, 1); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`; };
  const lastDay = (m) => (m === TODAY.slice(0, 7) ? Number(TODAY.slice(8)) : daysIn(m));

  const TX = [];
  let seq = 1;
  const push = (t) => TX.push({ id: 't' + seq++, card: pick(CARDS), memo: '', ...t });

  MONTHS.forEach((m) => {
    const limit = lastDay(m);
    FIXED.forEach((f) => {
      if (f.day > limit) return;
      const amount = Array.isArray(f.amount) ? roundTo(randInt(f.amount[0], f.amount[1]), 10) : f.amount;
      push({ date: `${m}-${pad(f.day)}`, merchant: f.merchant, amount, kind: 'fixed', category: f.category, tags: [...f.tags], card: '신한카드' });
    });
    INSTALLMENTS.forEach((i) => {
      const idx = monthDiff(i.start, m) + 1;
      if (idx < 1 || idx > i.months || i.day > limit) return;
      push({ date: `${m}-${pad(i.day)}`, merchant: i.merchant, amount: i.monthly, kind: 'installment', category: i.category, tags: [...i.tags], card: i.card, installment: { planId: i.id, current: idx, total: i.months, principal: i.principal } });
    });
    GENERAL.forEach((g) => {
      const mult = (g.boost && g.boost[m]) || 1;
      const count = Math.round(randInt(g.n[0], g.n[1]) * mult);
      for (let k = 0; k < count; k++) {
        const day = randInt(1, limit);
        push({ date: `${m}-${pad(day)}`, merchant: pick(g.merchants), amount: roundTo(randInt(g.range[0], g.range[1])), kind: 'general', category: g.category, tags: [...pick(g.tags)] });
      }
    });
  });
  EVENTS.forEach((e) => push({ kind: 'general', ...e }));
  TX.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : b.amount - a.amount));

  // ---------- helpers ----------
  const fmt = (n) => Math.round(n).toLocaleString('ko-KR');
  const won = (n) => fmt(n) + '원';
  const man = (n) => { const v = n / 10000; return (v >= 100 ? Math.round(v).toLocaleString('ko-KR') : v.toFixed(1).replace(/\.0$/, '')) + '만'; };
  const monthLabel = (m) => { const [y, mo] = m.split('-'); return `${y}년 ${Number(mo)}월`; };
  const shortMonth = (m) => `${Number(m.split('-')[1])}월`;
  const inMonth = (m) => TX.filter((t) => t.date.startsWith(m));
  const sum = (list) => list.reduce((s, t) => s + t.amount, 0);
  const groupSum = (list, keyFn) => {
    const o = {};
    list.forEach((t) => [].concat(keyFn(t)).forEach((k) => { o[k] = (o[k] || 0) + t.amount; }));
    return Object.entries(o).sort((a, b) => b[1] - a[1]);
  };
  const byKind = (list) => {
    const o = { general: 0, fixed: 0, installment: 0 };
    list.forEach((t) => { o[t.kind] += t.amount; });
    return o;
  };
  const pct = (cur, prev) => (prev ? ((cur - prev) / prev) * 100 : null);
  const allTags = () => groupSum(TX, (t) => t.tags).map(([k]) => k);

  const installmentStatus = (m) => INSTALLMENTS.map((i) => {
    const current = monthDiff(i.start, m) + 1;
    return { ...i, current, active: current >= 1 && current <= i.months, remaining: Math.max(0, i.months - current), remainingAmount: Math.max(0, i.months - current) * i.monthly, endMonth: addMonths(i.start, i.months - 1) };
  }).filter((i) => i.active);

  // 다음 n개월 약정 지출(고정비 + 할부) 전망
  const commitmentForecast = (fromMonth, n) => {
    const fixedBase = FIXED.reduce((s, f) => s + (Array.isArray(f.amount) ? (f.amount[0] + f.amount[1]) / 2 : f.amount), 0);
    return Array.from({ length: n }, (_, k) => {
      const m = addMonths(fromMonth, k + 1);
      const ins = INSTALLMENTS.filter((i) => { const c = monthDiff(i.start, m) + 1; return c >= 1 && c <= i.months; });
      return { month: m, fixed: roundTo(fixedBase, 1000), installment: ins.reduce((s, i) => s + i.monthly, 0), plans: ins.map((i) => i.merchant) };
    });
  };

  window.LEDGER = {
    TODAY, MONTHS, KINDS, CATEGORIES, CARDS, FIXED, INSTALLMENTS, TX,
    fmt, won, man, monthLabel, shortMonth, inMonth, sum, groupSum, byKind, pct, allTags,
    daysIn, lastDay, addMonths, monthDiff, installmentStatus, commitmentForecast,
  };
})();
