package com.xpay.pay.proxy.ips.useropen.rsp;

import com.xpay.pay.proxy.ips.common.ResponseHead;

/**
 * Created by sunjian on Date: 2017/12/25 下午4:23
 * Description:
 */
public class OpenUserRespXml {

  private ResponseHead head;

  private Body body;

  public ResponseHead getHead() {
    return head;
  }

  public void setHead(ResponseHead head) {
    this.head = head;
  }

  public Body getBody() {
    return body;
  }

  public void setBody(Body body) {
    this.body = body;
  }
}