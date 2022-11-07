package io.pivotal.quotes.service;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import io.pivotal.quotes.domain.*;
import io.pivotal.quotes.exception.SymbolNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

/**
 * A service to retrieve Company and Quote information.
 * 
 * @author David Ferreira Pinto
 *
 */
@Service
@RefreshScope
@Slf4j
public class QuoteService {

	protected String quote_url;

	protected String quotes_url;

	protected String company_url;

	public static final String FMT = "json";

	/*
     * cannot autowire as don't want ribbon here.
	 */
	private RestTemplate restTemplate = new RestTemplate();

	/**
	 * Retrieves an up to date quote for the given symbol.
	 * 
	 * @param symbol
	 *            The symbol to retrieve the quote for.
	 * @return The quote object or null if not found.
	 * @throws SymbolNotFoundException
	 */
	@HystrixCommand(fallbackMethod = "getQuoteFallback")
	public Quote getQuote(String symbol) throws SymbolNotFoundException {

		log.debug("QuoteService.getQuote: retrieving quote for: " + symbol);

		Map<String, String> params = new HashMap<String, String>();
		params.put("symbol", symbol);

		IexQuote quote = restTemplate.getForObject(quote_url, IexQuote.class, params);

		if (quote == null || quote.getSymbol() == null) {
			throw new SymbolNotFoundException("Symbol not found: " + symbol);
		}

		log.debug("QuoteService.getQuote: retrieved quote: " + quote);


		return QuoteMapper.INSTANCE.mapFromIexQuote(quote);

	}

	@SuppressWarnings("unused")
	private Quote getQuoteFallback(String symbol)
			throws SymbolNotFoundException {
		log.debug("QuoteService.getQuoteFallback: circuit opened for symbol: "
				+ symbol);
		Quote quote = getDummyQuote(1, symbol);
		return quote;
	}
	
	/**
	 * Retrieve multiple quotes at once.
	 * 
	 * @param symbols
	 *            comma delimited list of symbols.
	 * @return a list of quotes.
	 */
	@HystrixCommand(fallbackMethod = "getQuotesFallback")
	public List<Quote> getQuotes(String symbols) {
		log.debug("retrieving multiple quotes for: " + symbols);

		IexBatchQuote batchQuotes = restTemplate.getForObject(quotes_url, IexBatchQuote.class, symbols);

		log.debug("Got response: " + batchQuotes);
		final List<Quote> quotes = new ArrayList<>();

		Arrays.asList(symbols.split(",")).forEach(symbol -> {
			if(batchQuotes.containsKey(symbol)) {
				quotes.add(QuoteMapper.INSTANCE.mapFromIexQuote(batchQuotes.get(symbol).get("quote")));
			} else {
				log.warn("Quote could not be found for the following symbol: " + symbol);
				Quote quote = new Quote();
				quote.setSymbol(symbol);
				quote.setStatus("FAILED");
				quotes.add(quote);
			}
		});

		return quotes;
	}

	@SuppressWarnings("unused")
	private List<Quote> getQuotesFallback(String symbols)
			throws SymbolNotFoundException {
		log.debug("QuoteService.getQuotesFallback: circuit opened for symbol: "
				+ symbols);
		
		final List<Quote> quotes = new ArrayList<>();
		
		
		Arrays.asList(symbols.split(",")).forEach(symbol -> {
			log.warn("Generaing dummy Quote for the following symbol: " + symbol);
			Quote quote = getDummyQuote(quotes.size()+1, symbol);
			quotes.add(quote);
		});

		return quotes;
	}

	private Quote getDummyQuote(int i, String symbol) {
		Quote quote = new Quote();
		quote.setSymbol(symbol);
		quote.setName(symbol + " Corp LLC");
		quote.setCurrency("USD");
		quote.setLastPrice(new BigDecimal(i * 123.00));
		quote.setChange(new BigDecimal(i * 20.00));
		quote.setHigh(new BigDecimal(i * 132.00));
		quote.setLow(new BigDecimal(i * 103.00));
		quote.setVolume(i * 320);
		quote.setStatus("SUCCESS");
		return quote;
	}

	/**
	 * Retrieves a list of CompanyInfor objects. Given the name parameters, the
	 * return list will contain objects that match the search both on company
	 * name as well as symbol.
	 * 
	 * @param name
	 *            The search parameter for company name or symbol.
	 * @return The list of company information.
	 */
	@HystrixCommand(fallbackMethod = "getCompanyInfoFallback",
		    commandProperties = {
		      @HystrixProperty(name="execution.timeout.enabled", value="false")
		    })
	public List<CompanyInfo> getCompanyInfo(String name) {
		log.debug("QuoteService.getCompanyInfo: retrieving info for: "
				+ name);
		Map<String, String> params = new HashMap<String, String>();
		params.put("name", name);
		CompanyInfo[] companies = restTemplate.getForObject(company_url,
				CompanyInfo[].class, params);
		log.debug("QuoteService.getCompanyInfo: retrieved info: "
				+ companies);
		return Arrays.asList(companies);
	}


	@SuppressWarnings("unused")
	private List<CompanyInfo> getCompanyInfoFallback(String symbol)
			throws SymbolNotFoundException {
		log.debug("QuoteService.getCompanyInfoFallback: circuit opened for symbol: "
				+ symbol);
		List<CompanyInfo> companies = new ArrayList<>();
		
		CompanyInfo company = new CompanyInfo();
		company.setSymbol(symbol);
		company.setName(symbol + " Corp LLC");
		company.setExchange("NASDAQ");
		companies.add(company);

		return companies;
	}
}
