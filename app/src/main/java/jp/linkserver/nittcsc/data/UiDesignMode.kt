package jp.linkserver.nittcsc.data

enum class UiDesignMode {
    MATERIAL_3,
    MATERIAL_3_EXPRESSIVE;

    fun effective(expressiveAvailable: Boolean): UiDesignMode =
        if (this == MATERIAL_3_EXPRESSIVE && !expressiveAvailable) MATERIAL_3 else this

    companion object {
        fun fromStoredValue(value: String?): UiDesignMode =
            entries.firstOrNull { it.name == value } ?: MATERIAL_3
    }
}
