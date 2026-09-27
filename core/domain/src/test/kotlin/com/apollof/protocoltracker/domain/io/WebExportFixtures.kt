package com.apollof.protocoltracker.domain.io

/** Web app exports for [WebExportImportTest]: a realistic trimmed full export, a messy one and an AI-review file. */
object WebExportFixtures {
    /** The full export (`/api/export/full`), trimmed; built from the web app's code paths (research: web-export §5). */
    val FULL = """
        {"user":"florian","exported_at":"2026-09-26T19:42:07.512093",
         "settings":{"cycle_start_date":"2026-05-04","testosterone_schedule":"EOD","vial_test_mgml":"250",
           "testosterone_ester":"cypionate",
           "active_tracker_compounds":"[\"testosterone\",\"hcg\",\"anavar_am\",\"telmisartan\",\"bpc157\"]",
           "blood_levels_dose_plan":"{\"testosterone\":[{\"effective_date\":\"2026-06-01\",\"dose\":400}]}"},
         "doses":{"test_cyp":400.0,"hcg":250.0,"anavar_am":10.0,"telmisartan":20.0},
         "logs":[
          {"id":2210,"user":"florian","type":"blood_pressure","created_at":"2026-09-26T06:12:44.918263",
           "data":{"sys":131,"dia":84,"hr":62,"notes":""}},
          {"id":2208,"user":"florian","type":"testosterone","created_at":"2026-09-25T19:30:12.004118",
           "data":{"dose_mg":114,"volume_ml":0.46,"concentration_mg_ml":250,"site":"Left delt","notes":"bit sore"}},
          {"id":2209,"user":"florian","type":"hcg","created_at":"2026-09-25T19:30:12.004118",
           "data":{"dose_iu":250,"dose":250,"unit":"IU","notes":""}},
          {"id":2195,"user":"florian","type":"anavar","created_at":"2026-09-20T10:00:00",
           "data":{"source":"tracker","tracker_key":"2026-W38::Sun::anavar_am","iso_week":"2026-W38","day":"Sun",
                   "compound":"anavar_am","peptide_id":null,"dose":10,"unit":"mg","dose_mg":10,"timing":"AM"}},
          {"id":2190,"user":"florian","type":"telmisartan","created_at":"2026-09-19T07:05:00","data":{"dose_mg":20,"notes":""}},
          {"id":2101,"user":"florian","type":"blood_pressure","created_at":"2026-09-12T18:40:00","data":{"sys":128,"dia":80,"hr":70}},
          {"id":1733,"user":"florian","type":"note","created_at":"2026-08-30T21:15:03.221000",
           "data":{"text":"  Slept badly, headache in the evening.  "}},
          {"id":1402,"user":"florian","type":"mood","created_at":"2026-07-03T08:00:00","data":{"mood_level":7,"notes":""}},
          {"id":12,"user":"florian","type":"injection","created_at":"2026-05-05T17:20:31.101552",
           "data":{"compound":"Reta","site":"Left delt","notes":""}}
         ],
         "symptoms":[
          {"id":388,"user":"florian","created_at":"2026-09-24T20:41:07.221000","aromasin_dose_mg":0.0,
           "notes":"Oily skin after the gym","anastrozole_dose_mg":0.0,"dbol_dose_mg":0.0,"mood_level":6.0,
           "hair_shedding_level":2.0,"symptoms":["acne","extreme_oiliness","water_retention"]},
          {"id":381,"user":"florian","created_at":"2026-09-20T07:02:11.004512","aromasin_dose_mg":10.0,
           "notes":"","anastrozole_dose_mg":0.0,"dbol_dose_mg":0.0,"mood_level":null,"hair_shedding_level":null,"symptoms":[]},
          {"id":360,"user":"florian","created_at":"2026-09-02T09:10:00","aromasin_dose_mg":null,
           "notes":"","anastrozole_dose_mg":null,"dbol_dose_mg":10.0,"mood_level":null,"hair_shedding_level":null,"symptoms":[]},
          {"id":301,"user":"florian","created_at":"2026-08-11T12:00:00","aromasin_dose_mg":null,
           "notes":"","anastrozole_dose_mg":null,"dbol_dose_mg":null,"mood_level":null,"hair_shedding_level":null,
           "symptoms":["high_e2","bloating"]},
          {"id":7,"user":"florian","created_at":"2026-05-09T21:00:00.500000","aromasin_dose_mg":0.0,
           "notes":"","anastrozole_dose_mg":null,"dbol_dose_mg":null,"mood_level":null,"hair_shedding_level":null,
           "symptoms":["night_sweats"]}
         ],
         "checklist":[
          {"iso_week":"2026-W39","day":"Fri","compound":"bpc157","checked":1,"updated_at":"2026-09-25T18:03:12.550911"},
          {"iso_week":"2026-W39","day":"Thu","compound":"anavar_am","checked":0,"updated_at":"2026-09-24T09:00:41.000512"}
         ],
         "weekly_goals":[{"iso_week":"2026-W39","custom_note":"Keep BP under 135"},{"iso_week":"2026-W30","custom_note":""}],
         "bloodwork":[
          {"id":9,"test_date":"2026-08-14","notes":"","pdf_filename":"florian_2026-08-14.pdf","has_pdf":true,"markers":[]},
          {"id":4,"test_date":"2026-06-05","notes":"Fasted, 8:30","pdf_filename":null,"has_pdf":false,
           "markers":[
            {"key":"total_testosterone","name":"Total Testosterone","category":"Hormones",
             "us":{"value":3605.0,"unit":"ng/dL"},"si":{"value":125.0,"unit":"nmol/L"},
             "ref_low":264,"ref_high":916,"target_low":800,"target_high":null,"target_note":"web text",
             "target_flag":"in","flag":"high"},
            {"key":"estradiol","name":"Estradiol (E2)","category":"Hormones",
             "us":{"value":103.98,"unit":"pg/mL"},"si":{"value":381.7,"unit":"pmol/L"},
             "ref_low":10,"ref_high":40,"target_low":30,"target_high":60,"target_note":"web text",
             "target_flag":"out","flag":"crit_high"},
            {"key":"hematocrit","name":"Hematocrit","category":"Hematology",
             "us":{"value":48.0,"unit":"%"},"si":{"value":0.48,"unit":"L/L"},
             "ref_low":40,"ref_high":50.0,"target_low":null,"target_high":52,"target_note":"web text",
             "target_flag":"in","flag":"ok"},
            {"key":"creatinine","name":"Creatinine","category":"Kidney",
             "us":{"value":0.96,"unit":"mg/dL"},"si":{"value":84.998,"unit":"µmol/L"},
             "ref_low":0.67,"ref_high":1.17,"target_low":0.67,"target_high":1.17,"target_note":"web text",
             "target_flag":"in","flag":"ok"},
            {"key":"vitamin_d","name":"vitamin_d","category":"Other",
             "us":{"value":75.0,"unit":""},"si":null,"ref_low":null,"ref_high":null,"flag":"ok"}
           ]}
         ]}
    """.trimIndent()

    /** Hand-edited and odd rows: string and float numbers, impossible values, zones, unreadable rows. */
    val MESSY = """
        {"user":"florian","exported_at":"2026-09-26T19:42:07",
         "logs":[
          {"id":"3001","type":"blood_pressure","created_at":"2026-09-01T07:00:00","data":{"sys":"131","dia":84.4,"hr":0}},
          {"id":3002,"type":"blood_pressure","created_at":"2026-09-02T07:00:00Z","data":{"sys":122,"dia":79,"hr":null,"notes":" after coffee "}},
          {"id":3003,"type":"blood_pressure","created_at":"2026-09-03T09:00:00+02:00","data":{"sys":40,"dia":30}},
          {"id":3004,"type":"blood_pressure","created_at":"2026-09-04T07:00:00","data":{"sys":120,"dia":130}},
          {"id":3005,"type":"blood_pressure","created_at":"2026-09-05T07:00:00","data":{"sys":"high","dia":80}},
          {"id":3006,"type":"note","created_at":"2026-09-06T07:00:00","data":{"text":"   "}},
          {"id":3007,"type":"note","data":{"text":"no time"}},
          {"type":"note","created_at":"2026-09-07T07:00:00","data":{"text":"no id"}},
          {"id":3009,"type":"note","created_at":"yesterday","data":{"text":"bad time"}},
          42,
          {"id":3010,"type":"mood","created_at":"2026-07-01T08:00:00","data":{"mood_level":0,"notes":"Flat day"}},
          {"id":3011,"type":"proviron","created_at":"2026-09-08T07:00:00","data":{"dose_mg":25}}
         ],
         "symptoms":[
          {"id":501,"created_at":"2026-09-09T20:00:00","mood_level":0.0,"hair_shedding_level":9,"notes":null,
           "symptoms":["acne","acne"," high_e2 ",7,""]},
          {"id":502,"created_at":"2026-09-10T20:00:00","mood_level":12,"hair_shedding_level":null,"notes":"","symptoms":[]},
          {"id":503,"created_at":"2026-09-11T20:00:00","mood_level":"8","anastrozole_dose_mg":0.5,"symptoms":["insomnia"]}
         ],
         "bloodwork":[
          {"id":20,"test_date":"2026-07-10","notes":"","markers":[
            {"key":"hemoglobin","us":{"value":146.0,"unit":"g/dL"},"si":{"value":90.627,"unit":"mmol/L"},"ref_low":13.5,"ref_high":17.5},
            {"key":"hematocrit","us":{"value":49.0,"unit":"%"},"si":{"value":0.49,"unit":"L/L"},"ref_low":40,"ref_high":52},
            {"key":"prolactin","name":"prolactin","us":{"value":210.0,"unit":""},"si":null,"ref_low":null,"ref_high":null},
            {"key":"Ferritine_","us":{"value":120.0,"unit":""},"si":null,"ref_low":30,"ref_high":400},
            {"key":"crp","us":{"value":-1.0,"unit":""},"si":null},
            {"key":"lh","us":null,"si":null},
            {"us":{"value":1.0}}
          ]},
          {"id":21,"test_date":"2026-07-20","notes":"","markers":[
            {"key":"hemoglobin","us":{"value":150.0,"unit":"g/dL"},"si":{"value":93.11,"unit":"mmol/L"}}
          ]},
          {"id":22,"test_date":"2026-13-01","markers":[{"key":"lh","us":{"value":5.0,"unit":"IU/L"},"si":{"value":5.0,"unit":"IU/L"}}]}
         ]}
    """.trimIndent()

    /** The web Logs page's "Export AI Review" file (no `user` key, capped lists) with [logs] blood pressure rows. */
    fun aiReview(logs: Int): String {
        val rows = (1..logs).joinToString(",") { i ->
            val at = java.time.LocalDateTime.of(2026, 1, 1, 7, 0).plusHours(i.toLong())
            """{"id":$i,"type":"blood_pressure","created_at":"${at}:00.000000","data":{"sys":${110 + i % 40},"dia":${70 + i % 20},"notes":""}}"""
        }
        return """
            {"exported_at":"2026-09-26T19:42:07.512Z","profile":"florian","purpose":"AI review","current_context":{},
             "settings":{},"doses":{},"logs":[$rows],
             "symptoms":[{"id":1,"created_at":"2026-09-01T08:00:00","mood_level":5,"symptoms":[],"notes":""}],
             "bloodwork":[],"e2_calibration":null,"checklist":[],"protocol":{},"notes_for_ai":"text"}
        """.trimIndent()
    }
}
