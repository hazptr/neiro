package neiro.extension

import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.data.extension.model.NetworkExtensionStore
import mihon.data.extension.model.toAvailableExtensions
import org.junit.jupiter.api.Test

class ProtoStoreIconTest {

    // Written by neiro-extensions' index_pb2 (protobuf 7.35.1): a store with one extension, the first with the store's
    // iconUrl (field 501) and the extension's audio flag (field 601), the second without either.
    private val withFields =
        "0a01531201531a016b220f0a0d68747470733a2f2f652e6f7267aa065d0a5b0a01411201611a2a0a1368747470" +
            "733a2f2f652e6f72672f612e61706b121368747470733a2f2f652e6f72672f612e706e672203312e362801320131380142180801" +
            "1201411a02656e220d68747470733a2f2f652e6f7267c82501aa1f3668747470733a2f2f6b6569796f757368692e6769746875" +
            "622e696f2f616e64726f69642d6368726f6d652d313932783139322e706e67"
    private val withoutFields = "0a01531201531a016b220f0a0d68747470733a2f2f652e6f7267aa065a0a580a01411201611a2a0a1368" +
        "747470733a2f2f652e6f72672f612e61706b121368747470733a2f2f652e6f72672f612e706e672203312e3628013201313801" +
        "421808011201411a02656e220d68747470733a2f2f652e6f7267"

    @OptIn(ExperimentalStdlibApi::class)
    private fun decode(hex: String) = ProtoBuf.decodeFromByteArray<NetworkExtensionStore>(hex.hexToByteArray())

    private fun store(hex: String) = decode(hex).toExtensionStore("https://example.org/index.pb")

    private fun extension(hex: String) = decode(hex).extensionList!!.toAvailableExtensions(store(hex)).single()

    @Test
    fun `reads the store icon from field 501`() {
        store(withFields).iconUrl shouldBe "https://keiyoushi.github.io/android-chrome-192x192.png"
    }

    @Test
    fun `stores without an icon have none`() {
        store(withoutFields).iconUrl shouldBe null
    }

    @Test
    fun `reads the music flag from field 601`() {
        extension(withFields).hasAudio shouldBe true
        extension(withoutFields).hasAudio shouldBe false
    }
}
