package com.imyvm.iwg.infra.config

import com.imyvm.hoki.config.Option
import com.typesafe.config.Config

object RuleConfig {
    @JvmField
    val RULE_DEFAULT_SPAWN_MONSTERS = Option("core.rule.spawn_monsters.default", true,
        "the default monster spawning rule in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val RULE_DEFAULT_SPAWN_PHANTOMS = Option("core.rule.spawn_phantoms.default", true,
        "the default phantom spawning rule in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val RULE_DEFAULT_TNT_BLOCK_PROTECTION = Option("core.rule.tnt_block_protection.default", false,
        "the default TNT block protection rule in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val RULE_DEFAULT_ENDERMAN_BLOCK_PICKUP = Option("core.rule.enderman_block_pickup.default", true,
        "the default enderman block pickup rule in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val RULE_DEFAULT_SCULK_SPREAD = Option("core.rule.sculk_spread.default", true,
        "the default sculk spread rule in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val RULE_DEFAULT_SNOW_GOLEM_TRAIL = Option("core.rule.snow_golem_trail.default", true,
        "the default snow golem trail rule in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val RULE_DEFAULT_DISPENSER = Option("core.rule.dispenser.default", true,
        "the default dispenser rule in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val RULE_DEFAULT_PRESSURE_PLATE = Option("core.rule.pressure_plate.default", true,
        "the default pressure plate rule in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val RULE_DEFAULT_PISTON = Option("core.rule.piston.default", true,
        "the default piston rule in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }
}