package com.example.jipsa.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** 매월 고정비 자동 기록(PRD 3.2)을 위해 스케줄링을 켠다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration
