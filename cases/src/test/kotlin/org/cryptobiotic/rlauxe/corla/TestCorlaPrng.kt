package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.corlaInput.Colorado2026Primary
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class TestCorlaPrng {

    @Test
    fun testCorlaPrng() {
        // apparently using BigInteger, not long
        // ULong.MAX_VALUE = 18446744073709551615
        //                   49006417086137856424
        //val seed: ULong = 49006417086137856424UL

        // modulus max - min + 1 = 1_444_047
        val corlaPRNG = CorlaPRNG("49006417086137856424", true, 1, 1_444_047)
        val (digest, bigint, pick) = corlaPRNG.digest(1)
        println("digest = ${digest.contentToString()}")
        println("bigint = $bigint")
        println("pick = $pick")
    }

    @Test
    fun testCorla2026pPrngStatewide() {
        val stateInput = Colorado2026Primary()
        val ccmvrs = stateInput.cardComparison().mvrs.associate {
            "${it.countyName}: ${it.imprintedId}" to it
        }
        println("ccmvrs (${ccmvrs.size})")

        val mvrsByDbid = stateInput.cardComparison().mvrs.associate {
            it.dbid to it
        }
        println("stateIds (${stateIds.size})")
        stateIds.forEach { stateId ->
            val mvr = mvrsByDbid[stateId]
            if (mvr == null) {
                println("stateId $stateId has no mvr")
            } else if (mvr.countyName == "El Paso") {
                println("  ${mvrsByDbid[stateId]}")
            }
        }

        //ccmvrs.toSortedMap().forEach { println("  ${it}")}
        //println()

        val stateManifest = stateInput.statewideManifest
        // modulus max - min + 1 = 1_444_047
        val corlaPRNG = CorlaPRNG("49006417086137856424", true, 1, 1_444_047)

        val prns = corlaPRNG.getRandomNumbers(1, 250)
        val ids = prns.map { prn -> stateManifest.idFromIndex(prn) }

        ids.forEachIndexed { idx, id ->
            val mvr = ccmvrs[id]
            if ( id.startsWith("El Paso")) {
                print("$idx: ${prns[idx]} -> $id == $mvr")
                if (mvr == null)
                    println("  *** MISSS")
                else println()
            }
            // assertTrue(ccmvrs.contains(id))
        }
    }

    @Test
    fun testCorla2026pPrngCounty() {
        val county = "El Paso"
        val stateInput = Colorado2026Primary()
        // stateInput.counties().forEach { county ->
            println("-------------- $county")
            val ccmvrs = stateInput.cardComparison().mvrs.filter { it.countyName == county }.map {
                "${it.countyName}: ${it.imprintedId}"
            }
            println("ccmvrs (${ccmvrs.size})")

            // ccmvrs.sorted().forEach { println("  ${it}") }
            // println()

            val strata = stateInput.strataMap[county]!!
            val countyManifest = stateInput.corlaCountyManifest(county)!!
            // modulus max - min + 1 = 1_444_047
            val corlaPRNG = CorlaPRNG("49006417086137856424", true, 1, strata.ballotCardCount)

        val prns = corlaPRNG.getRandomNumbers(1, strata.nmvrs)
        val ids = prns.map { prn -> countyManifest.idFromIndex(prn) }

        ids.forEachIndexed { idx, id ->
                print("$idx: ${prns[idx]} -> $id")
                if (!ccmvrs.contains(id))
                    println("$idx: $id *** MISSS")
                else println()
                // assertTrue(ccmvrs.contains(id))
            }
       // }
    }
}
// 50376,United States Senator - DEM,"[574781,528018,536634,557965,...

private val stateIds = setOf(574781,528018,536634,557965,595174,534771,550695,513642,549262,577928,566134,579643,584727,522451,573614,540074,518037,593122,522472,769636,842827,846366,743181,828901,831107,714730,813802,790555,791667,772608,774413,726884,769207,749956,854362,727949,767013,803135,1613562,1534705,1518426,1548945,1599491,1555412,1585305,1563554,1583192,1589646,1522033,1563776,1598185,1553597,1546522,1515822,1552251,1593309,1512415,1515068,347410,1038062,1366511,1372288,1371918,184424,324284,307408,274255,295416,320894,259211,337871,236642,328954,280693,178730,181730,252962,337187,190030,336377,236755,169947,330229,271371,255310,343719,301113,307005,330308,264224,334604,325353,222083,661185,689024,715330,679957,698982,619919,675638,697409,688126,620327,661721,687369,613422,351195,110647,1147000,1146193,1161568,1108243,1143601,1205338,1234798,1139267,1184861,1138705,1154692,1089779,1228223,1209706,1113057,1099427,1165340,1155829,1147335,1093534,1162498,1131328,86528,87252,85902,365499,367913,370284,369046,152500,151834,152275,920914,990937,872345,991189,975456,966803,886778,951709,1019185,860924,904680,1013237,1016657,1003923,867274,900117,924979,941292,863491,917457,24159,1435701,1392277,1455893,1472005,1404547,1418585,1472628,1423338,1381486,1453528,1472669,1420731,1381437,95959,46557,59908,72741,61207,29725,42890,503366,1060361,1063350,1060952,1067014,1062572,148939,149760,9963,1036082,1257550,1281703,1246013,155568,162253,1483359,1484616,81329,1361079,1337236,1352419,1316665,1304377,1338364,1344113,1353087,1344489,1347089,492431)

/*
stateIds (210)
  ComparisonMvr(countyName=El Paso, imprintedId=201-189-73, dbid=1147000, statewide=false)
  ComparisonMvr(countyName=El Paso, imprintedId=401-164-29, dbid=1146193, statewide=true)
  ComparisonMvr(countyName=El Paso, imprintedId=401-198-78, dbid=1161568, statewide=true)
  ...

we find a mvrComparison line with  db=1146193, imprint 401-164-29.

El Paso,United States Senator - DEM,401-164-29,32-DEM,"""John Hickenlooper""","""John Hickenlooper""",YES,uploaded,"",2026-07-14 11:28:58.053067,1146193,STATE_WIDE_CONTEST

But that id doesnt come up in our prns for El Paso:
8: 745664 -> El Paso: 201-296-64 == null  *** MISSS
27: 697356 -> El Paso: 101-340-29 == null  *** MISSS
41: 821578 -> El Paso: 401-271-1 == null  *** MISSS
59: 824792 -> El Paso: 401-304-93 == null  *** MISSS
62: 668474 -> El Paso: 101-24-37 == null  *** MISSS
64: 812792 -> El Paso: 401-176-81 == null  *** MISSS
71: 725001 -> El Paso: 201-69-49 == null  *** MISSS
99: 797552 -> El Paso: 401-11-33 == null  *** MISSS
101: 743092 -> El Paso: 201-268-34 == null  *** MISSS
102: 687640 -> El Paso: 101-232-26 == null  *** MISSS
106: 782844 -> El Paso: 301-269-44 == null  *** MISSS
111: 819650 -> El Paso: 401-250-49 == null  *** MISSS
119: 770228 -> El Paso: 301-134-23 == null  *** MISSS
120: 829093 -> El Paso: 401-351-2 == null  *** MISSS
121: 683295 -> El Paso: 101-184-91 == null  *** MISSS
130: 803050 -> El Paso: 401-70-97 == null  *** MISSS
134: 798893 -> El Paso: 401-27-45 == null  *** MISSS
163: 749890 -> El Paso: 201-343-59 == null  *** MISSS
169: 745707 -> El Paso: 201-297-13 == null  *** MISSS
172: 783102 -> El Paso: 301-272-7 == null  *** MISSS
176: 732310 -> El Paso: 201-151-74 == null  *** MISSS
196: 693102 -> El Paso: 101-290-56 == null  *** MISSS
202: 749343 -> El Paso: 201-337-96 == null  *** MISSS
212: 718840 -> El Paso: 101-594-7 == null  *** MISSS
213: 701027 -> El Paso: 101-380-58 == null  *** MISSS
215: 716525 -> El Paso: 101-550-86 == null  *** MISSS
220: 809253 -> El Paso: 401-137-94 == null  *** MISSS
232: 786239 -> El Paso: 301-304-20 == null  *** MISSS
249: 732816 -> El Paso: 201-157-82 == null  *** MISSS

You might  suspect that ElPaso manifest is wrong, but the county PRNs succeed:
-------------- El Paso
ccmvrs (276)
0: 20815 -> El Paso: 101-232-8
1: 10508 -> El Paso: 101-122-16
2: 153580 -> El Paso: 401-258-9
3: 69837 -> El Paso: 201-198-96
4: 101208 -> El Paso: 301-111-61
5: 68962 -> El Paso: 201-189-97
6: 34438 -> El Paso: 101-382-89
7: 119858 -> El Paso: 301-308-54
8: 75985 -> El Paso: 201-265-24
9: 89918 -> El Paso: 201-416-8
10: 50556 -> El Paso: 101-561-62
11: 71013 -> El Paso: 201-211-77
12: 26520 -> El Paso: 101-293-83
13: 117192 -> El Paso: 301-281-25
14: 11770 -> El Paso: 101-135-58
15: 56059 -> El Paso: 201-45-83
16: 13249 -> El Paso: 101-151-35
17: 47691 -> El Paso: 101-529-42
18: 11917 -> El Paso: 101-137-12
19: 113260 -> El Paso: 301-240-40
20: 12012 -> El Paso: 101-138-7
21: 60272 -> El Paso: 201-92-71
22: 39648 -> El Paso: 101-441-47
23: 30563 -> El Paso: 101-340-43
24: 90451 -> El Paso: 201-422-28
25: 143346 -> El Paso: 401-147-42
26: 90655 -> El Paso: 201-424-44
27: 95592 -> El Paso: 301-52-61
28: 66740 -> El Paso: 201-165-48
29: 159677 -> El Paso: 401-323-50
30: 99721 -> El Paso: 301-96-10
31: 29952 -> El Paso: 101-333-98
32: 113116 -> El Paso: 301-238-90
33: 44892 -> El Paso: 101-499-31
34: 149075 -> El Paso: 401-210-20
35: 68687 -> El Paso: 201-187-21
36: 5236 -> El Paso: 101-65-75
37: 135540 -> El Paso: 401-62-72
38: 108231 -> El Paso: 301-186-52
39: 24072 -> El Paso: 101-265-45
40: 48068 -> El Paso: 101-533-46
41: 121550 -> El Paso: 301-326-36
42: 20320 -> El Paso: 101-226-3
43: 122226 -> El Paso: 301-333-35
44: 158854 -> El Paso: 401-314-2
45: 54194 -> El Paso: 201-23-98
46: 85096 -> El Paso: 201-364-12
47: 27091 -> El Paso: 101-300-33
48: 162564 -> El Paso: 401-353-89
49: 30073 -> El Paso: 101-335-36
50: 9578 -> El Paso: 101-112-72
51: 27180 -> El Paso: 101-301-23
52: 65202 -> El Paso: 201-148-58
53: 65779 -> El Paso: 201-154-62
54: 40322 -> El Paso: 101-449-62
55: 90981 -> El Paso: 301-2-50
56: 27694 -> El Paso: 101-307-26
57: 106865 -> El Paso: 301-172-55
58: 132740 -> El Paso: 401-34-18
59: 93005 -> El Paso: 301-24-15
60: 58939 -> El Paso: 201-78-32
61: 121145 -> El Paso: 301-321-80
62: 98872 -> El Paso: 301-86-39
63: 164931 -> El Paso: 501-56-94
64: 435 -> El Paso: 101-6-2
65: 2944 -> El Paso: 101-40-97
66: 68431 -> El Paso: 201-184-64
67: 56714 -> El Paso: 201-53-3
68: 927 -> El Paso: 101-11-23
69: 141270 -> El Paso: 401-125-74
70: 58628 -> El Paso: 201-74-5
71: 126766 -> El Paso: 301-381-88
72: 60489 -> El Paso: 201-94-89
73: 140565 -> El Paso: 401-116-79
74: 51643 -> El Paso: 101-582-18
75: 66901 -> El Paso: 201-167-11
76: 49462 -> El Paso: 101-548-22
77: 6562 -> El Paso: 101-80-99
78: 153585 -> El Paso: 401-258-14
79: 131789 -> El Paso: 401-24-43
80: 91984 -> El Paso: 301-13-72
81: 95734 -> El Paso: 301-54-5
82: 61409 -> El Paso: 201-104-84
83: 124856 -> El Paso: 301-361-30
84: 105760 -> El Paso: 301-160-24
85: 146161 -> El Paso: 401-178-61
86: 10658 -> El Paso: 101-123-67
87: 79466 -> El Paso: 201-303-69
88: 5210 -> El Paso: 101-65-49
89: 7494 -> El Paso: 101-90-50
90: 129775 -> El Paso: 401-1-40
91: 13166 -> El Paso: 101-150-51
92: 118007 -> El Paso: 301-289-50
93: 26987 -> El Paso: 101-298-68
94: 86778 -> El Paso: 201-382-47
95: 108395 -> El Paso: 301-188-51
96: 134953 -> El Paso: 401-56-78
97: 65637 -> El Paso: 201-153-18
98: 83559 -> El Paso: 201-348-42
99: 109639 -> El Paso: 301-201-37
100: 153170 -> El Paso: 401-253-80
101: 37023 -> El Paso: 101-411-47
102: 140733 -> El Paso: 401-118-52
103: 31473 -> El Paso: 101-350-69
104: 83090 -> El Paso: 201-343-66
105: 80261 -> El Paso: 201-313-30
106: 147325 -> El Paso: 401-190-52
107: 27959 -> El Paso: 101-310-1
108: 124983 -> El Paso: 301-362-57
109: 57190 -> El Paso: 201-58-91
110: 125185 -> El Paso: 301-364-71
111: 103510 -> El Paso: 301-135-17
112: 81304 -> El Paso: 201-324-1
113: 6574 -> El Paso: 101-81-11
114: 61446 -> El Paso: 201-105-28
115: 160676 -> El Paso: 401-333-99
116: 24724 -> El Paso: 101-274-3
117: 48997 -> El Paso: 101-543-31
118: 162403 -> El Paso: 401-352-27
119: 97618 -> El Paso: 301-73-66
120: 80029 -> El Paso: 201-310-21
121: 4828 -> El Paso: 101-60-60
122: 59835 -> El Paso: 201-88-32
123: 19477 -> El Paso: 101-217-25
124: 140754 -> El Paso: 401-118-73
125: 117263 -> El Paso: 301-281-96
126: 75479 -> El Paso: 201-260-5
127: 113596 -> El Paso: 301-243-82
128: 17514 -> El Paso: 101-195-55
129: 84760 -> El Paso: 201-360-70
130: 7341 -> El Paso: 101-88-95
131: 40283 -> El Paso: 101-449-23
132: 85986 -> El Paso: 201-374-21
133: 79345 -> El Paso: 201-302-43
134: 108575 -> El Paso: 301-190-33
135: 78922 -> El Paso: 201-297-35
136: 105868 -> El Paso: 301-161-35
137: 149631 -> El Paso: 401-216-51
138: 16555 -> El Paso: 101-185-67
139: 81821 -> El Paso: 201-329-23
140: 11259 -> El Paso: 101-130-11
141: 138679 -> El Paso: 401-97-31
142: 75573 -> El Paso: 201-260-99
143: 13974 -> El Paso: 101-159-35
144: 132278 -> El Paso: 401-29-43
145: 26288 -> El Paso: 101-290-49
146: 157370 -> El Paso: 401-298-51
147: 5762 -> El Paso: 101-71-19
148: 128780 -> El Paso: 301-402-75
149: 59215 -> El Paso: 201-81-20
150: 139747 -> El Paso: 401-108-42
151: 7914 -> El Paso: 101-95-10
152: 21198 -> El Paso: 101-235-97
153: 162873 -> El Paso: 401-357-13
154: 7952 -> El Paso: 101-95-48
155: 69918 -> El Paso: 201-199-77
156: 47697 -> El Paso: 101-529-48
157: 50013 -> El Paso: 101-554-23
158: 87324 -> El Paso: 201-388-77
159: 49066 -> El Paso: 101-544-8
160: 127609 -> El Paso: 301-390-55
161: 51870 -> El Paso: 101-589-65
162: 25962 -> El Paso: 101-286-65
163: 96955 -> El Paso: 301-66-89
164: 88569 -> El Paso: 201-401-96
165: 65676 -> El Paso: 201-153-57
166: 82733 -> El Paso: 201-340-2
167: 149264 -> El Paso: 401-212-13
168: 151831 -> El Paso: 401-240-19
169: 131960 -> El Paso: 401-26-17
170: 158076 -> El Paso: 401-305-87
171: 97095 -> El Paso: 301-68-36
172: 117167 -> El Paso: 301-280-97
173: 111770 -> El Paso: 301-224-3
174: 84336 -> El Paso: 201-356-35
175: 118707 -> El Paso: 301-296-61
176: 26974 -> El Paso: 101-298-55
177: 110238 -> El Paso: 301-207-65
178: 34715 -> El Paso: 101-385-71
179: 83888 -> El Paso: 201-351-73
180: 53113 -> El Paso: 201-9-100
181: 121045 -> El Paso: 301-320-79
182: 132643 -> El Paso: 401-33-21
183: 144012 -> El Paso: 401-155-17
184: 47449 -> El Paso: 101-526-88
185: 144537 -> El Paso: 401-160-63
186: 147092 -> El Paso: 401-188-15
187: 147232 -> El Paso: 401-189-58
188: 42451 -> El Paso: 101-474-3
189: 30812 -> El Paso: 101-343-96
190: 73783 -> El Paso: 201-241-38
191: 28512 -> El Paso: 101-318-24
192: 114203 -> El Paso: 301-250-2
193: 158134 -> El Paso: 401-306-47
194: 62450 -> El Paso: 201-116-31
195: 21579 -> El Paso: 101-239-86
196: 130109 -> El Paso: 401-4-75
197: 160097 -> El Paso: 401-327-81
198: 15106 -> El Paso: 101-170-91
199: 61033 -> El Paso: 201-100-45
200: 103666 -> El Paso: 301-136-75
201: 3350 -> El Paso: 101-45-18
202: 15400 -> El Paso: 101-173-89
203: 60717 -> El Paso: 201-97-22
204: 70146 -> El Paso: 201-202-6
205: 43465 -> El Paso: 101-484-46
206: 104229 -> El Paso: 301-142-53
207: 141918 -> El Paso: 401-132-51
208: 98226 -> El Paso: 301-79-87
209: 66620 -> El Paso: 201-164-23
210: 129163 -> El Paso: 301-408-53
211: 100993 -> El Paso: 301-109-32
212: 64489 -> El Paso: 201-139-4
213: 149191 -> El Paso: 401-211-37
214: 89088 -> El Paso: 201-407-25
215: 18872 -> El Paso: 101-210-76
216: 132474 -> El Paso: 401-31-49
217: 153507 -> El Paso: 401-257-32
218: 13379 -> El Paso: 101-152-70
219: 121400 -> El Paso: 301-324-39
220: 91491 -> El Paso: 301-8-71
221: 139849 -> El Paso: 401-109-49
222: 55103 -> El Paso: 201-34-31
223: 101373 -> El Paso: 301-113-31
224: 11825 -> El Paso: 101-136-15
225: 35346 -> El Paso: 101-392-19
226: 146750 -> El Paso: 401-184-66
227: 55618 -> El Paso: 201-40-31
228: 43904 -> El Paso: 101-489-9
229: 164867 -> El Paso: 501-56-30
230: 82489 -> El Paso: 201-337-49
231: 68741 -> El Paso: 201-187-75
232: 21554 -> El Paso: 101-239-61
233: 10337 -> El Paso: 101-120-44
234: 127543 -> El Paso: 301-389-85
235: 153823 -> El Paso: 401-261-27
236: 42056 -> El Paso: 101-468-48
237: 84036 -> El Paso: 201-353-29
238: 146723 -> El Paso: 401-184-39
239: 158720 -> El Paso: 401-312-62
240: 16087 -> El Paso: 101-180-83
241: 80523 -> El Paso: 201-315-96
242: 92171 -> El Paso: 301-15-59
243: 44653 -> El Paso: 101-496-80243: El Paso: 101-496-80 *** MISSS
244: 36773 -> El Paso: 101-408-79244: El Paso: 101-408-79 *** MISSS
245: 37747 -> El Paso: 101-418-86245: El Paso: 101-418-86 *** MISSS
246: 69163 -> El Paso: 201-192-4246: El Paso: 201-192-4 *** MISSS
247: 48414 -> El Paso: 101-537-13247: El Paso: 101-537-13 *** MISSS
248: 62836 -> El Paso: 201-121-12248: El Paso: 201-121-12 *** MISSS
249: 5907 -> El Paso: 101-73-31249: El Paso: 101-73-31 *** MISSS
250: 53694 -> El Paso: 201-17-88250: El Paso: 201-17-88 *** MISSS
251: 70802 -> El Paso: 201-208-80251: El Paso: 201-208-80 *** MISSS
252: 138858 -> El Paso: 401-99-21252: El Paso: 401-99-21 *** MISSS
253: 75946 -> El Paso: 201-264-83253: El Paso: 201-264-83 *** MISSS
254: 131456 -> El Paso: 401-21-6254: El Paso: 401-21-6 *** MISSS
255: 135524 -> El Paso: 401-62-56255: El Paso: 401-62-56 *** MISSS
256: 6655 -> El Paso: 101-81-92256: El Paso: 101-81-92 *** MISSS
257: 110777 -> El Paso: 301-213-18257: El Paso: 301-213-18 *** MISSS
258: 49077 -> El Paso: 101-544-19258: El Paso: 101-544-19 *** MISSS
259: 28446 -> El Paso: 101-317-58259: El Paso: 101-317-58 *** MISSS
260: 87400 -> El Paso: 201-389-54260: El Paso: 201-389-54 *** MISSS
261: 157928 -> El Paso: 401-304-36261: El Paso: 401-304-36 *** MISSS
262: 8692 -> El Paso: 101-103-52262: El Paso: 101-103-52 *** MISSS
263: 70567 -> El Paso: 201-206-38263: El Paso: 201-206-38 *** MISSS
264: 158230 -> El Paso: 401-307-50264: El Paso: 401-307-50 *** MISSS
265: 143981 -> El Paso: 401-154-84265: El Paso: 401-154-84 *** MISSS

Only thing I can think of is that the ElPaso manifest changed between doing the statewide draw and the county draws.

We have a similar thing happening with LaPlate, county works but statewide doesnt. We also have the LaPlata CVRS and
we know that there are 50 extra CVRs in it, ie that the manifest we have for them is wrong.

So the theory is that they did the county draws with the manifests that we have, and then both LaPlata and ElPaso
 had to submit new manifests, and then they did the statewide draw.

 So to validate both draws we need two sets of manifests.

 Why didnt Claude see this problem?
 */