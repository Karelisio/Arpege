package com.karelisio.arpege.update

/** Version X.Y.Z comparable (un éventuel préfixe « v » est ignoré). */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        fun parse(s: String): SemVer? {
            val parts = s.trim().removePrefix("v").substringBefore('-').split('.')
            if (parts.size != 3) return null
            val n = parts.map { it.toIntOrNull() ?: return null }
            return SemVer(n[0], n[1], n[2])
        }
    }
}
