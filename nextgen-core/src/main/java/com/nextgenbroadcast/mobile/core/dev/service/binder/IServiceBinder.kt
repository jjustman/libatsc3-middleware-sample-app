package com.nextgenbroadcast.mobile.core.dev.service.binder

import com.nextgenbroadcast.mobile.core.dev.service.presentation.IControllerPresenter

interface IServiceBinder {
    val controllerPresenter: IControllerPresenter?
}