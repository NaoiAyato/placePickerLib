package com.xteng.placepicker.inject

import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.KoinComponent

object KoinContext {

    var koinApp: KoinApplication? = null

}

interface KoinComponent : KoinComponent {

    override fun getKoin(): Koin = KoinContext.koinApp?.koin!!

}