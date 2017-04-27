package com.xpay.pay.controller;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

import com.xpay.pay.model.App;
import com.xpay.pay.service.AppService;
import com.xpay.pay.util.JsonUtils;

public class TokenServlet extends HttpServlet {
	private static final long serialVersionUID = -4617898543988945707L;
	
	protected final Logger logger = LogManager.getLogger(TokenServlet.class);
	
	@Autowired
	protected AppService appService;
	
	@Override
	 public void init(ServletConfig config) throws ServletException {
		SpringBeanAutowiringSupport.processInjectionBasedOnServletContext(this,
				config.getServletContext());
	}
	
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	String uri = request.getRequestURI();
  		
		response.setCharacterEncoding("utf-8");
		response.setHeader("Content-type", "application/json;charset=UTF-8");
		TokenResponse tokenResp = null;
		try {
			request.setCharacterEncoding("utf-8");
	        logger.info("Refresh token from "+ uri);
	        String appId = uri.substring(uri.lastIndexOf("/")+1);
	        String token = this.refreshToken(appId);
	        if(token!=null) {
	        	tokenResp = new TokenResponse(token);
	        }
    	} catch (Exception e) {
            e.printStackTrace();
        } finally {
        	if(tokenResp!=null) {
        		response.getWriter().write(JsonUtils.toJson(tokenResp));
        	} else {
        		response.sendError(404, "App not found");
        	}
        } 
    }
    
    private String refreshToken(String appId) {
    	App app = appService.findByKey(appId);
    	if(app!=null) {
    		appService.refreshToken(app);
    		return app.getToken()+appId;
    	}
    	return null;
    }
    
	public static class TokenResponse {
		private String token;
		public TokenResponse(String token) {
			this.token = token;
		}
		public String getToken() {
			return token;
		}
		public void setToken(String token) {
			this.token = token;
		}
	}
}