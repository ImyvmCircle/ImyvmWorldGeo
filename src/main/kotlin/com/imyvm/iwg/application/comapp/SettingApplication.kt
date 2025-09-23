package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.domain.EffectKey
import com.imyvm.iwg.domain.PermissionKey
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.RuleKey
import net.minecraft.server.network.ServerPlayerEntity

fun onHandleSetting(
    player: ServerPlayerEntity,
    region: Region,
    scopeName: String?,
    keyString: String,
    valueString: String,
    isPersonal: Boolean,
    targetPlayerStr: String
){

}

private fun isPermissionKey(key: String) = runCatching { PermissionKey.valueOf(key) }.isSuccess
private fun isEffectKey(key: String) = runCatching { EffectKey.valueOf(key) }.isSuccess
private fun isRuleKey(key: String) = runCatching { RuleKey.valueOf(key) }.isSuccess
