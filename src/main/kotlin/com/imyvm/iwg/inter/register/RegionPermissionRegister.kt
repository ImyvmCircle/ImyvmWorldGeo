package com.imyvm.iwg.inter.register

import com.imyvm.iwg.application.regionapp.registerPlayerBuildBreakPermission
import com.imyvm.iwg.application.regionapp.registerPlayerContainerInteractionPermission

fun registerRegionPermissions() {
    registerPlayerBuildBreakPermission()
    registerPlayerContainerInteractionPermission()
}