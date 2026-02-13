package com.finance.backend.service;

import com.finance.backend.model.Forex;
import com.finance.backend.repository.ForexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TcmbForexService {
    
    private final ForexRepository forexRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${TCMB_XML_URL}")
    private String tcmbXmlUrl;
    
    @Transactional
    public List<Forex> fetchAndSaveTcmbRates() {
        try {
            log.info("Fetching forex rates from TCMB...");
            String xmlContent = restTemplate.getForObject(tcmbXmlUrl, String.class);
            List<Forex> forexList = parseXml(xmlContent);
            List<Forex> savedList = new ArrayList<>();
            for (Forex forex : forexList) {
                Forex existing = forexRepository.findByCurrencyCode(forex.getCurrencyCode()).orElse(null);
                
                if (existing != null) {
                    existing.setCurrencyName(forex.getCurrencyName());
                    existing.setCurrencyNameTr(forex.getCurrencyNameTr());
                    existing.setUnit(forex.getUnit());
                    existing.setForexBuying(forex.getForexBuying());
                    existing.setForexSelling(forex.getForexSelling());
                    existing.setBanknoteBuying(forex.getBanknoteBuying());
                    existing.setBanknoteSelling(forex.getBanknoteSelling());
                    existing.setCrossRateUsd(forex.getCrossRateUsd());
                    existing.setCrossRateOther(forex.getCrossRateOther());
                    existing.setUpdatedAt(LocalDateTime.now());
                    existing.setTcmbUpdatedAt(LocalDateTime.now());
                    savedList.add(forexRepository.save(existing));
                } else {
                    forex.setCreatedAt(LocalDateTime.now());
                    forex.setUpdatedAt(LocalDateTime.now());
                    forex.setTcmbUpdatedAt(LocalDateTime.now());
                    savedList.add(forexRepository.save(forex));
                }
            }
            
            log.info("Successfully saved {} forex rates from TCMB", savedList.size());
            return savedList;
            
        } catch (Exception e) {
            log.error("Error fetching TCMB forex rates", e);
            throw new RuntimeException("Failed to fetch TCMB forex rates", e);
        }
    }
    
    private List<Forex> parseXml(String xmlContent) throws Exception {
        List<Forex> forexList = new ArrayList<>();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes("UTF-8")));
        
        NodeList currencyNodes = doc.getElementsByTagName("Currency");
        
        for (int i = 0; i < currencyNodes.getLength(); i++) {
            Element currencyElement = (Element) currencyNodes.item(i);
            
            String kod = currencyElement.getAttribute("Kod");
            
            if ("XDR".equals(kod)) {
                continue;
            }
            
            Forex forex = new Forex();
            forex.setCurrencyCode(kod + "TRY");
            
            String unitStr = getElementText(currencyElement, "Unit");
            Integer unit = unitStr != null && !unitStr.isEmpty() ? Integer.parseInt(unitStr) : 1;
            forex.setUnit(unit);
            
            forex.setCurrencyNameTr(getElementText(currencyElement, "Isim"));
            forex.setCurrencyName(getElementText(currencyElement, "CurrencyName"));
            
            BigDecimal forexBuying = parseBigDecimal(getElementText(currencyElement, "ForexBuying"));
            BigDecimal forexSelling = parseBigDecimal(getElementText(currencyElement, "ForexSelling"));
            BigDecimal banknoteBuying = parseBigDecimal(getElementText(currencyElement, "BanknoteBuying"));
            BigDecimal banknoteSelling = parseBigDecimal(getElementText(currencyElement, "BanknoteSelling"));
            BigDecimal crossRateUsd = parseBigDecimal(getElementText(currencyElement, "CrossRateUSD"));
            
            if (unit > 1) {
                BigDecimal unitDivisor = new BigDecimal(unit);
                forex.setForexBuying(forexBuying != null ? forexBuying.divide(unitDivisor, 4, RoundingMode.HALF_UP) : null);
                forex.setForexSelling(forexSelling != null ? forexSelling.divide(unitDivisor, 4, RoundingMode.HALF_UP) : null);
                forex.setBanknoteBuying(banknoteBuying != null ? banknoteBuying.divide(unitDivisor, 4, RoundingMode.HALF_UP) : null);
                forex.setBanknoteSelling(banknoteSelling != null ? banknoteSelling.divide(unitDivisor, 4, RoundingMode.HALF_UP) : null);
            } else {
                forex.setForexBuying(forexBuying);
                forex.setForexSelling(forexSelling);
                forex.setBanknoteBuying(banknoteBuying);
                forex.setBanknoteSelling(banknoteSelling);
            }
        
            if (unit > 1 && crossRateUsd != null) {
                forex.setCrossRateUsd(crossRateUsd.divide(new BigDecimal(unit), 4, RoundingMode.HALF_UP));
            } else {
                forex.setCrossRateUsd(crossRateUsd);
            }
            forex.setCrossRateOther(parseBigDecimal(getElementText(currencyElement, "CrossRateOther")));
            
            forexList.add(forex);
        }
        
        return forexList;
    }
    
    private String getElementText(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            String text = nodeList.item(0).getTextContent().trim();
            return text.isEmpty() ? null : text;
        }
        return null;
    }
    
    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse BigDecimal: {}", value);
            return null;
        }
    }
}
