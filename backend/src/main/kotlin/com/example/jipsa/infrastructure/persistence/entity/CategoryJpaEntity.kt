package com.example.jipsa.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 카테고리 영속화 엔티티. 도메인 모델과의 변환은 매퍼만 담당한다. */
@Entity
@Table(name = "category")
class CategoryJpaEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "name", nullable = false, length = 20)
    var name: String,

    @Column(name = "icon", nullable = false, length = 8)
    var icon: String,

    @Column(name = "color", nullable = false, length = 7)
    var color: String,

) : BaseTimeEntity()
