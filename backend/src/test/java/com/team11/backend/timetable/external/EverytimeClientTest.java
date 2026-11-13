package com.team11.backend.timetable.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class EverytimeClientTest {

    // 2026-11-13에 https://everytime.kr/@XBnJpRXLiTn3lRiMWEv6 에서 실제로 받은 응답 형태.
    private static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <response>
              <table year="2026" semester="2" status="1" identifier="XBnJpRXLiTn3lRiMWEv6">
                <subject id="8096439">
                  <internal value="109075-21001"/>
                  <name value="오픈소스소프트웨어"/>
                  <professor value="최성록"/>
                  <time value="목(1 ~ 4)">
                    <data day="3" starttime="108" endtime="156" place="미래관-319 미래관-320 미래관-B104"/>
                  </time>
                  <place value="미래관-319 미래관-320 미래관-B104"/>
                  <credit value="3"/>
                  <closed value="0"/>
                </subject>
                <subject id="8096454">
                  <internal value="109261-21001"/>
                  <name value="웹프로그래밍"/>
                  <professor value="이길흥"/>
                  <time value="화(1 ~ 4)">
                    <data day="1" starttime="108" endtime="156" place="미래관-320"/>
                  </time>
                  <place value="미래관-320"/>
                  <credit value="3"/>
                  <closed value="0"/>
                </subject>
              </table>
            </response>
            """;

    private final EverytimeClient client = new EverytimeClient();

    @Test
    void parse_extractsSubjectsAndTimes() {
        List<EverytimeClient.Subject> subjects = client.parse(SAMPLE_XML);

        assertThat(subjects).hasSize(2);
        EverytimeClient.Subject first = subjects.get(0);
        assertThat(first.id()).isEqualTo("8096439");
        assertThat(first.name()).isEqualTo("오픈소스소프트웨어");
        assertThat(first.times()).hasSize(1);
        EverytimeClient.Time time = first.times().get(0);
        assertThat(time.day()).isEqualTo(3); // 목
        assertThat(time.startUnit()).isEqualTo(108); // 09:00
        assertThat(time.endUnit()).isEqualTo(156); // 13:00
        assertThat(time.place()).isEqualTo("미래관-319 미래관-320 미래관-B104");
    }

    @Test
    void parse_whenTableStatusIsPrivate_throws() {
        String privateXml = """
                <response>
                  <table status="-2"></table>
                </response>
                """;

        assertThatThrownBy(() -> client.parse(privateXml))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.EVERYTIME_IMPORT_FAILED);
    }

    @Test
    void extractIdentifier_acceptsShareUrlAndRejectsOthers() {
        assertThat(client.extractIdentifier("https://everytime.kr/@XBnJpRXLiTn3lRiMWEv6"))
                .isEqualTo("XBnJpRXLiTn3lRiMWEv6");

        assertThatThrownBy(() -> client.extractIdentifier("https://everytime.kr/timetable"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.EVERYTIME_URL_INVALID);
    }

    @Test
    void toMinutesOfDay_convertsFiveMinuteUnitsAndClampsAtMidnight() {
        assertThat(EverytimeClient.toMinutesOfDay(108)).isEqualTo(9 * 60);
        assertThat(EverytimeClient.toMinutesOfDay(288)).isEqualTo(23 * 60 + 59);
    }
}
