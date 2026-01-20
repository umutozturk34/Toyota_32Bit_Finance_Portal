package com.finance.backend.client;

import com.finance.backend.entity.ExchangeRate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class TcmbApiClient {
    
    private static final String TCMB_URL = "https://www.tcmb.gov.tr/kurlar/today.xml";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    
    @Cacheable(value = "exchange-rates", key = "'today'")
    public List<ExchangeRate> fetchExchangeRates() {
        log.info("Fetching exchange rates from TCMB");
        List<ExchangeRate> rates = new ArrayList<>();
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            
            URL url = new URL(TCMB_URL);
            InputStream inputStream = url.openStream();
            Document doc = builder.parse(inputStream);
            
            String dateStr = doc.getDocumentElement().getAttribute("Date");
            LocalDate rateDate = LocalDate.parse(dateStr, DATE_FORMATTER);
            
            NodeList currencyNodes = doc.getElementsByTagName("Currency");
            
            for (int i = 0; i < currencyNodes.getLength(); i++) {
                Element currencyElement = (Element) currencyNodes.item(i);
                
                String code = currencyElement.getAttribute("CurrencyCode");
                String name = getElementValue(currencyElement, "CurrencyName");
                String forexBuying = getElementValue(currencyElement, "ForexBuying");
                String forexSelling = getElementValue(currencyElement, "ForexSelling");
                
                if (forexBuying != null && !forexBuying.isEmpty() && 
                    forexSelling != null && !forexSelling.isEmpty()) {
                    
                    ExchangeRate rate = new ExchangeRate();
                    rate.setCurrencyCode(code);
                    rate.setCurrencyName(name);
                    rate.setBuyingRate(new BigDecimal(forexBuying));
                    rate.setSellingRate(new BigDecimal(forexSelling));
                    rate.setRateDate(rateDate);
                    
                    rates.add(rate);
                }
            }
            
            log.info("Successfully fetched {} exchange rates for {}", rates.size(), rateDate);
            
        } catch (Exception e) {
            log.error("Error fetching exchange rates from TCMB: {}", e.getMessage(), e);
        }
        
        return rates;
    }
    
    private String getElementValue(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }
}
