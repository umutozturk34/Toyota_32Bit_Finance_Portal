package com.finance.backend.client;
import com.finance.backend.dto.external.TcmbRateDto;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.ForexMapper;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
@Component
@Slf4j
public class TcmbForexClient {
    private final RestTemplate restTemplate;
    private final ForexMapper forexMapper;
    private final String tcmbXmlUrl;
    public TcmbForexClient(RestTemplate restTemplate,
                           ForexMapper forexMapper,
                           @Value("${app.tcmb-xml-url}") String tcmbXmlUrl) {
        this.restTemplate = restTemplate;
        this.forexMapper = forexMapper;
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
            rates.add(forexMapper.toRateDto(el));
        }
        log.debug("Parsed {} currency rates from TCMB", rates.size());
        return rates;
    }
}
