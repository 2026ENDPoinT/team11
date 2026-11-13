package com.team11.backend.timetable.external;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * 에브리타임 "친구 시간표 보기" 공유 링크가 내부적으로 호출하는 공개 XML API를 그대로 호출한다
 * (POST https://api.everytime.kr/find/timetable/table/friend — 로그인/쿠키 불필요, 공개 시간표 전용).
 */
@Component
public class EverytimeClient {

    private static final Logger log = LoggerFactory.getLogger(EverytimeClient.class);
    private static final Pattern SHARE_URL_PATTERN =
            Pattern.compile("^https?://everytime\\.kr/@([A-Za-z0-9_-]+)/?(?:[?#].*)?$");
    private static final String API_URL = "https://api.everytime.kr/find/timetable/table/friend";
    private static final int MAX_MINUTES_OF_DAY = 23 * 60 + 59;

    private final RestClient restClient;

    public EverytimeClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public record Subject(String id, String name, List<Time> times) {
    }

    /** day: 0=월 ~ 6=일(timetable.domain.DayOfWeek 선언 순서와 같음). startUnit/endUnit: 5분 단위(0~288). */
    public record Time(int day, int startUnit, int endUnit, String place) {
    }

    public List<Subject> fetchSubjects(String shareUrl) {
        String identifier = extractIdentifier(shareUrl);
        String xml;
        try {
            xml = restClient.post()
                    .uri(API_URL)
                    .header("Origin", "https://everytime.kr")
                    .header("Referer", shareUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(buildForm(identifier))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("에브리타임 시간표 조회 실패: {}", e.toString());
            throw new ApiException(ErrorCode.EVERYTIME_IMPORT_FAILED);
        }
        return parse(xml);
    }

    public String extractIdentifier(String shareUrl) {
        Matcher matcher = SHARE_URL_PATTERN.matcher(shareUrl.trim());
        if (!matcher.matches()) {
            throw new ApiException(ErrorCode.EVERYTIME_URL_INVALID);
        }
        return matcher.group(1);
    }

    /** 5분 단위 숫자 → 분(0~1439). 자정을 넘기는 수업은 23:59로 잘라낸다(사실상 발생 안 함). */
    public static int toMinutesOfDay(int fiveMinuteUnit) {
        return Math.min(fiveMinuteUnit * 5, MAX_MINUTES_OF_DAY);
    }

    List<Subject> parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));

            NodeList tables = document.getElementsByTagName("table");
            if (tables.getLength() == 0) {
                throw new ApiException(ErrorCode.EVERYTIME_IMPORT_FAILED);
            }
            Element table = (Element) tables.item(0);
            String status = table.getAttribute("status");
            if ("-1".equals(status) || "-2".equals(status)) {
                throw new ApiException(ErrorCode.EVERYTIME_IMPORT_FAILED); // 비공개/없는 시간표
            }

            List<Subject> subjects = new ArrayList<>();
            NodeList subjectNodes = table.getElementsByTagName("subject");
            for (int i = 0; i < subjectNodes.getLength(); i++) {
                Element subjectEl = (Element) subjectNodes.item(i);
                List<Time> times = new ArrayList<>();
                NodeList dataNodes = subjectEl.getElementsByTagName("data");
                for (int j = 0; j < dataNodes.getLength(); j++) {
                    Element dataEl = (Element) dataNodes.item(j);
                    times.add(new Time(
                            Integer.parseInt(dataEl.getAttribute("day")),
                            Integer.parseInt(dataEl.getAttribute("starttime")),
                            Integer.parseInt(dataEl.getAttribute("endtime")),
                            dataEl.getAttribute("place")));
                }
                subjects.add(new Subject(subjectEl.getAttribute("id"), childAttrValue(subjectEl, "name"), times));
            }
            return subjects;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.EVERYTIME_IMPORT_FAILED);
        }
    }

    private static String childAttrValue(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? "" : ((Element) nodes.item(0)).getAttribute("value");
    }

    private static MultiValueMap<String, String> buildForm(String identifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("identifier", identifier);
        form.add("friendInfo", "false");
        return form;
    }
}
