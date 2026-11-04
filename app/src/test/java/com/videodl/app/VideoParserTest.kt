package com.videodl.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoParserTest {

    private val sample = """
    {
      "title": "Test video",
      "uploader": "Me",
      "extractor_key": "Youtube",
      "duration": 100,
      "thumbnail": "https://example.com/t.jpg",
      "formats": [
        {"format_id":"140","ext":"m4a","vcodec":"none","acodec":"mp4a.40.2","abr":128,"tbr":128,"protocol":"https"},
        {"format_id":"137","ext":"mp4","vcodec":"avc1.640028","acodec":"none","width":1920,"height":1080,"tbr":4000,"protocol":"https"},
        {"format_id":"399","ext":"mp4","vcodec":"av01.0.08M.08","acodec":"none","width":1920,"height":1080,"tbr":2500,"protocol":"https"},
        {"format_id":"248","ext":"webm","vcodec":"vp09.00.40.08","acodec":"none","width":1920,"height":1080,"tbr":2600,"protocol":"https"},
        {"format_id":"hls-1080","ext":"mp4","vcodec":"avc1.640028","acodec":"mp4a.40.2","width":1920,"height":1080,"tbr":6000,"protocol":"m3u8_native"},
        {"format_id":"sb0","ext":"mhtml","vcodec":"none","acodec":"none","width":48,"height":27,"protocol":"mhtml"}
      ]
    }
    """.trimIndent()

    @Test
    fun picksNormalH264LinkOverAv1Vp9AndHls() {
        val m = VideoParser.parse(sample)
        val video = m.qualities.first { !it.isAudioOnly }
        assertEquals("137", video.formatId)
        assertEquals("140", video.audioId)
        assertEquals(1080, video.res)
        assertTrue(m.qualities.none { it.formatId == "399" || it.formatId == "248" || it.formatId == "hls-1080" })
    }

    @Test
    fun addsAudioOnlyOptionLast() {
        val m = VideoParser.parse(sample)
        assertTrue(m.qualities.last().isAudioOnly)
        assertEquals("Test video", m.title)
    }

    @Test
    fun verticalVideoIsLabeledBySmallerSide() {
        val json = """
        {"title":"v","duration":10,"formats":[
          {"format_id":"h264_540","ext":"mp4","vcodec":"h264","acodec":"aac","width":720,"height":1280,"tbr":1500,"protocol":"https"}
        ]}
        """.trimIndent()
        val m = VideoParser.parse(json)
        assertEquals(720, m.qualities.first { !it.isAudioOnly }.res)
    }

    @Test
    fun fallsBackToBestWhenThereAreNoFormats() {
        val m = VideoParser.parse("""{"title":"x"}""")
        assertEquals(1, m.qualities.size)
        assertEquals("best", m.qualities[0].formatId)
    }
}
