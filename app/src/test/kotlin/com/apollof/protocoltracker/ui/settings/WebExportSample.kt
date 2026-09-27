package com.apollof.protocoltracker.ui.settings

import android.net.Uri
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * A small web app full export: 2 blood pressure readings, 1 note, 2 symptom logs and 1 draw with results (6 entries),
 * plus a dose and a weekly note that are not imported and a draw without results (a warning).
 */
internal object WebExportSample {
    val JSON = """
        {"user":"florian","exported_at":"2026-09-26T19:42:07.512093",
         "logs":[
          {"id":2210,"user":"florian","type":"blood_pressure","created_at":"2026-09-26T06:12:44.918263",
           "data":{"sys":131,"dia":84,"hr":62,"notes":""}},
          {"id":2208,"user":"florian","type":"testosterone","created_at":"2026-09-25T19:30:12.004118",
           "data":{"dose_mg":114,"volume_ml":0.46,"concentration_mg_ml":250,"site":"Left delt","notes":""}},
          {"id":2101,"user":"florian","type":"blood_pressure","created_at":"2026-09-12T18:40:00","data":{"sys":128,"dia":80,"hr":70}},
          {"id":1733,"user":"florian","type":"note","created_at":"2026-08-30T21:15:03.221000",
           "data":{"text":"Slept badly, headache in the evening."}}
         ],
         "symptoms":[
          {"id":388,"user":"florian","created_at":"2026-09-24T20:41:07.221000","notes":"Oily skin after the gym",
           "mood_level":6.0,"hair_shedding_level":2.0,"symptoms":["acne","water_retention"]},
          {"id":301,"user":"florian","created_at":"2026-08-11T12:00:00","notes":"","mood_level":null,
           "hair_shedding_level":null,"symptoms":["high_e2","bloating"]}
         ],
         "weekly_goals":[{"iso_week":"2026-W39","custom_note":"Keep BP under 135"}],
         "bloodwork":[
          {"id":9,"test_date":"2026-08-14","notes":"","pdf_filename":"lab.pdf","has_pdf":true,"markers":[]},
          {"id":4,"test_date":"2026-06-05","notes":"Fasted, 8:30","pdf_filename":null,"has_pdf":false,
           "markers":[
            {"key":"total_testosterone","name":"Total Testosterone","category":"Hormones",
             "us":{"value":1105.0,"unit":"ng/dL"},"si":{"value":38.3,"unit":"nmol/L"},"ref_low":264,"ref_high":916,"flag":"high"},
            {"key":"hematocrit","name":"Hematocrit","category":"Hematology",
             "us":{"value":48.0,"unit":"%"},"si":{"value":0.48,"unit":"L/L"},"ref_low":40,"ref_high":50.0,"flag":"ok"}
           ]}
         ]}
    """.trimIndent()

    const val ENTRIES = 6

    /** [JSON] in a new temporary file. */
    fun uri(): Uri = Uri.fromFile(File.createTempFile("web-export", ".json").apply { deleteOnExit(); writeText(JSON) })
}

/** Runs the main looper, where view models post their results, until [answer] gives a value. */
internal fun <T : Any> awaitMain(timeoutMs: Long = 60_000, answer: () -> T?): T {
    val end = System.currentTimeMillis() + timeoutMs
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        answer()?.let { return it }
        check(System.currentTimeMillis() < end) { "No answer within $timeoutMs ms" }
        Thread.sleep(10)
    }
}
