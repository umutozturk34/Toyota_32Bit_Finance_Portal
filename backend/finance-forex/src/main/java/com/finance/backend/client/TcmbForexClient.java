package com.finance.backend.client;
import com.finance.backend.dto.external.TcmbRateDto;
import com.finance.backend.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
@Component
@Slf4j
public class TcmbForexClient {
    private final RestTemplate restTemplate;
    private final String tcmbXmlUrl;
    public TcmbForexClient(RestTemplate restTemplate,
                           @Value("${app.tcmb-xml-url}") String tcmbXmlUrl) {
        this.restTemplate = restTemplate;
        this.tcmbXmlUrl = tcmbXmlUrl;
    }
    public List<TcmbRateDto> fetchDailyRates() {
        try {
            byte[] body = restTemplate.getForObject(tcmbXmlUrl, byte[].class);
            if (body == null || body.length == 0) {
                throw new ExternalApiException("TCMB", "Empty response from TCMB XML service");
            }
            return parseXml(new String(body, StandardCharsets.UTF_8));
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("TCMB", "Failed to fetch daily rates from TCMB", e);
        }
    }
    private List<TcmbRateDto> parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList nodes = doc.getElementsByTagName("Currency");
        List<TcmbRateDto> rates = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String code = el.getAttribute("Kod");
            if ("XDR".equals(code)) continue;
            rates.add(new TcmbRateDto(
                    code,
                    text(el, "CurrencyName"),
                    text(el, "Isim"),
                    toInt(text(el, "Unit"), 1),
                    toBigDecimal(text(el, "ForexBuying")),
                    toBigDecimal(text(el, "ForexSelling")),
                    toBigDecimal(text(el, "BanknoteBuying")),
                    toBigDecimal(text(el, "BanknoteSelling")),
                    toBigDecimal(text(el, "CrossRateUSD")),
                    toBigDecimal(text(el, "CrossRateOther"))
            ));
        }
        log.debug("Parsed {} currency rates from TCMB", rates.size());
        return rates;
    }
    private static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        String value = list.item(0).getTextContent().trim();
        return value.isEmpty() ? null : value;
    }
    private static int toInt(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return defaultValue; }
    }
    private static BigDecimal toBigDecimal(String value) {
        if (value == null) return null;
        try { return new BigDecimal(value); }
        catch (NumberFormatException e) { return null; }
    }
}
