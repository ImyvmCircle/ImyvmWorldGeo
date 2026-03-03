package com.imyvm.iwg.infra.config

import com.imyvm.hoki.config.Option
import com.typesafe.config.Config

object EntryExitConfig {
    @JvmField
    val ENTRY_EXIT_REGION_DELAY_SECONDS = Option("core.entry_exit.region_delay_seconds", 5,
        "the number of seconds a player must remain outside a region before the region entry or exit title is displayed.") { obj: Config, path: String? -> obj.getInt(path) }

    @JvmField
    val REGION_ENTER_I18N_KEY = Option("core.entry_exit.region_enter_i18n_key", "notification.region.enter",
        "the i18n key for the region entry notification message.") { obj: Config, path: String? -> obj.getString(path) }

    @JvmField
    val REGION_EXIT_I18N_KEY = Option("core.entry_exit.region_exit_i18n_key", "notification.region.exit",
        "the i18n key for the region exit notification message.") { obj: Config, path: String? -> obj.getString(path) }

    @JvmField
    val SCOPE_ENTER_I18N_KEY = Option("core.entry_exit.scope_enter_i18n_key", "notification.scope.enter",
        "the i18n key for the scope entry notification message.") { obj: Config, path: String? -> obj.getString(path) }

    @JvmField
    val SCOPE_EXIT_I18N_KEY = Option("core.entry_exit.scope_exit_i18n_key", "notification.scope.exit",
        "the i18n key for the scope exit notification message.") { obj: Config, path: String? -> obj.getString(path) }
}
