package com.sentinel.antiscamvn.utils

import android.content.Context
import android.content.SharedPreferences

object ProfileManager {
    private const val PREF_NAME = "user_profile_prefs"
    private const val KEY_NICKNAME = "user_nickname"
    private const val KEY_AVATAR_URI = "user_avatar_uri"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getNickname(context: Context): String {
        return getPrefs(context).getString(KEY_NICKNAME, "User") ?: "User"
    }

    fun setNickname(context: Context, nickname: String) {
        getPrefs(context).edit().putString(KEY_NICKNAME, nickname).apply()
    }

    fun getAvatarUri(context: Context): String? {
        return getPrefs(context).getString(KEY_AVATAR_URI, null)
    }

    fun setAvatarUri(context: Context, uri: String) {
        getPrefs(context).edit().putString(KEY_AVATAR_URI, uri).apply()
    }
}
