package com.xpay.pay.proxy.upay;

import static com.xpay.pay.model.StoreChannel.PaymentGateway.UPAY;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.KeyValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.xpay.pay.exception.GatewayException;
import com.xpay.pay.model.Bill;
import com.xpay.pay.proxy.IPaymentProxy;
import com.xpay.pay.proxy.PaymentRequest;
import com.xpay.pay.proxy.PaymentResponse;
import com.xpay.pay.proxy.PaymentResponse.OrderStatus;
import com.xpay.pay.util.AppConfig;
import com.xpay.pay.util.CommonUtils;
import com.xpay.pay.util.CryptoUtils;
import com.xpay.pay.util.JsonUtils;

@Component
public class UPayProxy implements IPaymentProxy {
	protected final Logger logger = LogManager.getLogger("AccessLog");
	
	private static final AppConfig config = AppConfig.UPayConfig;
	private static final String baseEndpoint = config.getProperty("provider.endpoint");
	private static final String jsPayEndpoint = config.getProperty("provider.jspay.endpoint");
	private static final String appId = config.getProperty("provider.app.id");
	private static final String appSecret = config.getProperty("provider.app.secret");
	@Autowired
	RestTemplate uPayProxy;
	
	public PaymentResponse unifiedOrder(PaymentRequest request) {
		String url = DEFAULT_JSAPI_URL + request.getOrderNo();
		PaymentResponse response = new PaymentResponse();
		response.setCode(PaymentResponse.SUCCESS);
		Bill bill = new Bill();
		bill.setCodeUrl(url);
		bill.setOrderStatus(OrderStatus.NOTPAY);
		response.setBill(bill);
		return response;
	}

	public String getJsUrl(PaymentRequest request) {
		UPayRequest upayRequest = this.toUPayRequest(UPAY.UnifiedOrder(), request);
		List<KeyValuePair> keyPairs = this.getKeyPairs(upayRequest);
		String sign = CryptoUtils.signQueryParams(keyPairs, "key", appSecret);
		String queryParams = CommonUtils.buildQueryParams(keyPairs, "sign", sign, "subject");
		String jsUrl = jsPayEndpoint + queryParams;
		logger.info("Redirect to: " + jsUrl);
		return jsUrl;
	}

	@Override
	public PaymentResponse query(PaymentRequest request) {
		String url = baseEndpoint + UPAY.Query();
		long l = System.currentTimeMillis();
		PaymentResponse response = null;
		try {
			UPayRequest upayRequest = new UPayRequest();
			upayRequest.setTerminal_sn(appId);
			upayRequest.setClient_sn(request.getOrderNo());
			String json = JsonUtils.toJson(upayRequest);
			String sign = appId + " " +CryptoUtils.md5(json + appSecret).toUpperCase();
			logger.info("query POST: " + url+", body "+JsonUtils.toJson(upayRequest));
			
			HttpHeaders headers = new HttpHeaders();
			headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
			headers.set("Authorization", sign);
			HttpEntity<?> httpEntity = new HttpEntity<>(upayRequest, headers);
			UPayResponse upayResponse = uPayProxy.exchange(url, HttpMethod.POST, httpEntity, UPayResponse.class).getBody();
			logger.info("query result: " + upayResponse.getError_code() + ", took "
					+ (System.currentTimeMillis() - l) + "ms");
			response = toPaymentResponse(request, upayResponse);
		} catch (RestClientException e) {
			logger.info("query failed, took " + (System.currentTimeMillis() - l) + "ms", e);
			throw e;
		}
		return response;
	}

	@Override
	public PaymentResponse refund(PaymentRequest request) {
		// TODO Auto-generated method stub
		return null;
	}
	
	private UPayRequest toUPayRequest(String method, PaymentRequest request) {
		UPayRequest upayRequest = new UPayRequest();
		upayRequest.setClient_sn(request.getOrderNo());
		upayRequest.setTerminal_sn(appId);
		upayRequest.setPayway(toPayway(request.getPayChannel()));
		upayRequest.setSubject(request.getSubject());
		if(StringUtils.isNotBlank(request.getTotalFee())) {
			upayRequest.setTotal_amount(String.valueOf((int)(request.getTotalFeeAsFloat()*100)));
		}
		upayRequest.setOperator("nayou001");
		upayRequest.setNotify_url(request.getNotifyUrl());
		upayRequest.setReturn_url(DEFAULT_JSAPI_URL + PAYED + "/" + request.getOrderNo());
		return upayRequest;
	}
	
	private List<KeyValuePair> getKeyPairs(UPayRequest paymentRequest) {
		if (paymentRequest == null) {
			return null;
		}
		List<KeyValuePair> keyPairs = new ArrayList<KeyValuePair>();
		
		if (StringUtils.isNotBlank(paymentRequest.getClient_sn())) {
			keyPairs.add(new KeyValuePair("client_sn", paymentRequest.getClient_sn()));
		}
		if (StringUtils.isNotBlank(paymentRequest.getTerminal_sn())) {
			keyPairs.add(new KeyValuePair("terminal_sn", paymentRequest.getTerminal_sn()));
		}
		if (StringUtils.isNotBlank(paymentRequest.getPayway())) {
			keyPairs.add(new KeyValuePair("payway", paymentRequest.getPayway()));
		}
		if (StringUtils.isNotBlank(paymentRequest.getSubject())) {
			keyPairs.add(new KeyValuePair("subject", paymentRequest.getSubject()));
		}
		if (StringUtils.isNotBlank(paymentRequest.getTotal_amount())) {
			keyPairs.add(new KeyValuePair("total_amount", paymentRequest.getTotal_amount()));
		}
		if (StringUtils.isNotBlank(paymentRequest.getOperator())) {
			keyPairs.add(new KeyValuePair("operator", paymentRequest.getOperator()));
		}
		if (StringUtils.isNotBlank(paymentRequest.getReturn_url())) {
			keyPairs.add(new KeyValuePair("return_url", paymentRequest.getReturn_url()));
		}
		if (StringUtils.isNotBlank(paymentRequest.getNotify_url())) {
			keyPairs.add(new KeyValuePair("notify_url", paymentRequest.getNotify_url()));
		}
		return keyPairs;
	}
	
	private String toPayway(PayChannel channel) {
		switch(channel) {
		case WECHAT: return "3";
		case ALIPAY: return "1";
		default: return "3";
		}
	}
	
	private PaymentResponse toPaymentResponse(PaymentRequest request, UPayResponse upayResponse) {
		if (upayResponse == null || !upayResponse.isSuccess()) {
			String code = upayResponse == null ? NO_RESPONSE : upayResponse.getError_code();
			String msg = upayResponse == null ? "No response" : upayResponse.getError_message();
			throw new GatewayException(code, msg);
		}
		PaymentResponse response = new PaymentResponse();
		response.setCode(PaymentResponse.SUCCESS);
		Bill bill = new Bill();
		bill.setOrderNo(request.getOrderNo());
		bill.setGatewayOrderNo(request.getGatewayOrderNo());
		bill.setOrderStatus(toOrderStatus(upayResponse.getBiz_response().getData().getOrder_status()));
		response.setBill(bill);
		return response;
	}
	
	public static OrderStatus toOrderStatus(String billStatus) {
		if("PAID".equals(billStatus) || "SUCCESS".equals(billStatus)) {
			return OrderStatus.SUCCESS;
		} else if("REFUNDED".equals(billStatus)) {
			return OrderStatus.REFUND;
		} else {
			return OrderStatus.NOTPAY;
		}
	}

}