package com.mitv.master.ui.navigation

sealed class MitvScreen(val route: String) {
    data object Splash : MitvScreen("splash")
    data object Login : MitvScreen("login")
    data object Home : MitvScreen("home")
    data object Player : MitvScreen("player/{channelId}") {
        fun createRoute(channelId: String) = "player/$channelId"
    }
}
