package pl.pickpack.panel.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pl.pickpack.panel.R

class NamedStatusAudioTest {

    @Test
    fun resolve_aniaAndAniaSz_areDistinct() {
        val ania = NamedStatusRef(356748, "ANIA")
        val aniaSz = NamedStatusRef(356749, "ANIA SZ")
        assertThat(NamedStatusAudio.resolveAudioKey(ania)).isEqualTo("ania")
        assertThat(NamedStatusAudio.resolveAudioKey(aniaSz)).isEqualTo("ania_sz")
        assertThat(NamedStatusAudio.rawResFor(ania)).isEqualTo(R.raw.uwaga_ania)
        assertThat(NamedStatusAudio.rawResFor(aniaSz)).isEqualTo(R.raw.uwaga_ania_sz)
    }

    @Test
    fun resolve_knownPeople_haveCustomClips() {
        val refs = listOf(
            NamedStatusRef(356750, "Bartek") to R.raw.uwaga_bartek,
            NamedStatusRef(356751, "Jakub") to R.raw.uwaga_jakub,
            NamedStatusRef(356752, "Kamil") to R.raw.uwaga_kamil,
            NamedStatusRef(356753, "Klaudia") to R.raw.uwaga_klaudia,
            NamedStatusRef(356754, "Michał") to R.raw.uwaga_michal,
        )
        refs.forEach { (ref, expected) ->
            assertThat(NamedStatusAudio.hasCustomAudio(ref)).isTrue()
            assertThat(NamedStatusAudio.rawResFor(ref)).isEqualTo(expected)
        }
    }

    @Test
    fun resolve_unknownPerson_fallsBackToGeneric() {
        val ref = NamedStatusRef(356757, "Łukasz")
        assertThat(NamedStatusAudio.resolveAudioKey(ref)).isEqualTo(NamedStatusAudio.FALLBACK_KEY)
        assertThat(NamedStatusAudio.hasCustomAudio(ref)).isFalse()
        assertThat(NamedStatusAudio.rawResFor(ref)).isEqualTo(R.raw.uwaga_imienny_status)
    }

    @Test
    fun explicitAudioKey_overridesName() {
        val ref = NamedStatusRef(999, "Someone", audioKey = "bartek")
        assertThat(NamedStatusAudio.resolveAudioKey(ref)).isEqualTo("bartek")
        assertThat(NamedStatusAudio.rawResFor(ref)).isEqualTo(R.raw.uwaga_bartek)
    }

    @Test
    fun overdueClips_dedupeSameRaw() {
        val statuses = listOf(
            NamedStatusRef(356748, "ANIA", audioKey = "ania"),
            NamedStatusRef(356750, "Bartek", audioKey = "bartek"),
        )
        val clips = NamedStatusAudio.rawResIdsForOverdue(
            overdueStatusIds = listOf(356748, 356748, 356750),
            statuses = statuses,
        )
        assertThat(clips).containsExactly(R.raw.uwaga_ania, R.raw.uwaga_bartek).inOrder()
    }

    @Test
    fun defaultSeed_customKeysForWiredPeople() {
        val seeded = NamedStatusAlertConfig.defaultSeed().associate { it.statusId to it.audioKey }
        assertThat(seeded[356748]).isEqualTo("ania")
        assertThat(seeded[356749]).isEqualTo("ania_sz")
        assertThat(seeded[356750]).isEqualTo("bartek")
        assertThat(seeded[356751]).isEqualTo("jakub")
        assertThat(seeded[356752]).isEqualTo("kamil")
        assertThat(seeded[356753]).isEqualTo("klaudia")
        assertThat(seeded[356754]).isEqualTo("michal")
        assertThat(seeded[356757]).isNull()
    }
}
