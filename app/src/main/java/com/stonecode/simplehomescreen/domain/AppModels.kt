package com.stonecode.simplehomescreen.domain

/** Stable launcher identity for an activity in a specific Android user profile. */
data class AppId(
    val componentName: String,
    val userSerial: Long,
) {
    init {
        require(componentName.isNotBlank()) { "componentName must not be blank" }
        require(userSerial >= 0) { "userSerial must not be negative" }
    }
}
@JvmInline
value class CategoryId(val value: String) {
    init {
        require(value.isNotBlank()) { "CategoryId must not be blank" }
    }
}

data class AppCategory(
    val id: CategoryId,
    val displayName: String,
)

object DefaultCategories {
    val Games = AppCategory(CategoryId("games"), "Games")
    val WorkAndFinance = AppCategory(CategoryId("work_finance"), "Work & Finance")
    val Communication = AppCategory(CategoryId("communication"), "Communication")
    val Media = AppCategory(CategoryId("media"), "Media")
    val Tools = AppCategory(CategoryId("tools"), "Tools")
    val PersonalApps = AppCategory(CategoryId("personal_apps"), "Personal Apps")
    val Other = AppCategory(CategoryId("other"), "Other")

    val all = listOf(
        Games,
        WorkAndFinance,
        Communication,
        Media,
        Tools,
        PersonalApps,
        Other,
    )
}
