package com.finance.market.viop.mapper;

import com.finance.market.viop.dto.ViopQuoteSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ViopHtmlSnapshotParserTest {

    private ViopHtmlSnapshotParser parser;

    @BeforeEach
    void setUp() {
        parser = new ViopHtmlSnapshotParser(new ViopSnapshotMapper());
    }

    @Test
    void should_parseValidRow_when_titleContainsPipeAndAllCellsPresent() {
        String html = "<table><tr>"
                + "<td title='F_USDTRY0626 | Dolar Vadeli'>F_USDTRY0626</td>"
                + "<td>35,40</td>"
                + "<td>1,15</td>"
                + "<td>0,40</td>"
                + "<td>12.345,67</td>"
                + "<td>100</td>"
                + "</tr></table>";

        List<ViopQuoteSnapshot> snaps = parser.parse(html);

        assertThat(snaps).hasSize(1);
        assertThat(snaps.get(0).symbol()).isEqualTo("F_USDTRY0626");
        assertThat(snaps.get(0).last()).isEqualByComparingTo("35.40");
        assertThat(snaps.get(0).volumeTry()).isEqualByComparingTo("12345.67");
        assertThat(snaps.get(0).volumeLot()).isEqualByComparingTo("100");
    }

    @Test
    void should_skipRow_when_lessThanFiveCells() {
        String html = "<table><tr>"
                + "<td title='F_X | name'>F_X</td>"
                + "<td>10</td>"
                + "</tr></table>";

        List<ViopQuoteSnapshot> snaps = parser.parse(html);

        assertThat(snaps).isEmpty();
    }

    @Test
    void should_skipRow_when_titleAttributeMissingPipe() {
        String html = "<table><tr>"
                + "<td title='no_pipe_here'>F_X</td>"
                + "<td>10</td><td>1</td><td>0,1</td><td>100</td>"
                + "</tr></table>";

        List<ViopQuoteSnapshot> snaps = parser.parse(html);

        assertThat(snaps).isEmpty();
    }

    @Test
    void should_skipRow_when_symbolBeforePipeIsBlank() {
        String html = "<table><tr>"
                + "<td title=' | name'>blank</td>"
                + "<td>10</td><td>1</td><td>0,1</td><td>100</td>"
                + "</tr></table>";

        List<ViopQuoteSnapshot> snaps = parser.parse(html);

        assertThat(snaps).isEmpty();
    }

    @Test
    void should_returnEmptyList_when_htmlContainsNoTableRows() {
        String html = "<html><body><p>no table</p></body></html>";

        List<ViopQuoteSnapshot> snaps = parser.parse(html);

        assertThat(snaps).isEmpty();
    }

    @Test
    void should_parseMultipleRows_when_multipleSymbolsPresent() {
        String html = "<table>"
                + "<tr>"
                + "<td title='F_USDTRY0626 | x'>x</td>"
                + "<td>35,40</td><td>1</td><td>0,40</td><td>1234</td>"
                + "</tr>"
                + "<tr>"
                + "<td title='F_EURTRY0626 | y'>y</td>"
                + "<td>38,50</td><td>0,5</td><td>0,20</td><td>500</td>"
                + "</tr>"
                + "</table>";

        List<ViopQuoteSnapshot> snaps = parser.parse(html);

        assertThat(snaps).hasSize(2);
        assertThat(snaps.get(0).symbol()).isEqualTo("F_USDTRY0626");
        assertThat(snaps.get(1).symbol()).isEqualTo("F_EURTRY0626");
    }

    @Test
    void should_returnNullForCell_when_textIsDash() {
        String html = "<table><tr>"
                + "<td title='F_X | x'>x</td>"
                + "<td>-</td><td>1</td><td>-</td><td>100</td>"
                + "</tr></table>";

        List<ViopQuoteSnapshot> snaps = parser.parse(html);

        assertThat(snaps).hasSize(1);
        assertThat(snaps.get(0).last()).isNull();
    }

    @Test
    void should_returnNullForCell_when_textIsNotANumber() {
        String html = "<table><tr>"
                + "<td title='F_X | x'>x</td>"
                + "<td>not-a-number</td><td>1</td><td>0,1</td><td>100</td>"
                + "</tr></table>";

        List<ViopQuoteSnapshot> snaps = parser.parse(html);

        assertThat(snaps).hasSize(1);
        assertThat(snaps.get(0).last()).isNull();
    }

    @Test
    void should_omitVolumeLot_when_sixthCellMissing() {
        String html = "<table><tr>"
                + "<td title='F_X | x'>x</td>"
                + "<td>10,00</td><td>1</td><td>0,10</td><td>100</td>"
                + "</tr></table>";

        List<ViopQuoteSnapshot> snaps = parser.parse(html);

        assertThat(snaps).hasSize(1);
        assertThat(snaps.get(0).volumeLot()).isNull();
    }
}
