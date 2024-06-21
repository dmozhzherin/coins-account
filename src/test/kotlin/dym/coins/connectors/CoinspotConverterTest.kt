package dym.coins.connectors

import dym.coins.coinspot.api.dto.TradeOperation
import dym.coins.coinspot.api.resource.OrderHistoryResponse
import dym.coins.coinspot.domain.AssetType
import dym.coins.coinspot.domain.OperationType
import dym.coins.tax.Config
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * @author dym
 * Date: 21.09.2023
 */
class CoinspotConverterTest {

    @Test
    fun buyOrderFrom() {
        val converter = CoinspotConverter(ZoneId.of("Australia/Sydney"))

        val tradeOp = TradeOperation(
            coin = AssetType.of("POE"),
            rate = "0.149904".toBigDecimal(),
            market = "POE/AUD",
            amount = "297.32362045".toBigDecimal(),
            type = OperationType.INSTANT,
            solddate = ZonedDateTime.parse("2018-01-18T15:14:33.780Z").toInstant(),
            total = "44.57".toBigDecimal(),
            audfeeExGst = "1.18014122".toBigDecimal(),
            audGst = "0.11801412".toBigDecimal(),
            audtotal = "44.57".toBigDecimal()
        )

        val logOp = converter.buyLogFrom(tradeOp)

        println(logOp)

        with(logOp) {
            assertTrue(incomingAsset == AssetType.of("POE"))
            assertTrue(outgoingAsset == AssetType.of("AUD"))
            assertEquals(LocalDate.parse("2018-01-19"), date)
            assertEquals(capital, (rate * incomingAmount).setScale(2, Config.DEFAULT_ROUNDING_MODE))
        }
    }

    @Test
    fun sellOrderFrom() {
        val converter = CoinspotConverter(ZoneId.of("Australia/Sydney"))

        val tradeOp = TradeOperation(
            coin = AssetType.of("LOOM"),
            rate = "0.099455".toBigDecimal(),
            market = "LOOM/AUD",
            amount = "492.18160892".toBigDecimal(),
            type = OperationType.TAKE_PROFIT,
            solddate = ZonedDateTime.parse("2023-09-20T12:20:43.619Z").toInstant(),
            total = "48.949921915138596".toBigDecimal(),
            audfeeExGst = "0.44949423".toBigDecimal(),
            audGst = "0.04494942".toBigDecimal(),
            audtotal = "48.95".toBigDecimal()
        )

        val logOp = converter.sellLogFrom(tradeOp)

        println(logOp)

        with(logOp) {
            assertTrue(incomingAsset== AssetType.of("AUD"))
            assertTrue(outgoingAsset == AssetType.of("LOOM"))
            assertEquals(LocalDate.parse("2023-09-20"), date)
            //When selling a coin for AUD, i.e. buying AUD for a coin, capital is equal to the rounded purchase amount
            assertEquals(capital, incomingAmount.setScale(2, Config.DEFAULT_ROUNDING_MODE))
        }

    }

}