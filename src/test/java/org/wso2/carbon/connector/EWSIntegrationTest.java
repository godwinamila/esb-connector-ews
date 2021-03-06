/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.connector;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.connector.integration.test.base.ConnectorIntegrationTestBase;
import org.wso2.connector.integration.test.base.RestResponse;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Integration Test for EWS Connector
 */
public class EWSIntegrationTest extends ConnectorIntegrationTestBase {

    private Map<String, String> esbRequestHeadersMap = new HashMap<String, String>();
    private Map<String, String> namespaceMap = new HashMap<String, String>();
    private String itemId, changeKey;

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {
        init("ews-connector-1.0.1-SNAPSHOT");
        esbRequestHeadersMap.put("Accept-Charset", "UTF-8");
        esbRequestHeadersMap.put("Content-Type", "text/xml");
        namespaceMap.put("m", "microsoft.com/exchange/services/2006/messages");
        namespaceMap.put("t", "http://schemas.microsoft.com/exchange/services/2006/types");
    }

    @Test(enabled = true, groups = {"wso2.esb"}, description = "EWS test case")
    public void testCreateItem() throws Exception {
        DefaultHttpClient defaultHttpClient = new DefaultHttpClient();
        // Sending Create Message Request
        String createItemProxyUrl = getProxyServiceURL("createItemOperation");
        RestResponse<OMElement> esbSoapResponse =
                sendXmlRestRequest(createItemProxyUrl, "POST", esbRequestHeadersMap, "CreateItem.xml");
        OMElement omElement = esbSoapResponse.getBody();
        OMElement createItemResponseMessageOmelement = omElement.getFirstElement().getFirstChildWithName(new QName
                ("http://schemas.microsoft.com/exchange/services/2006/messages", "CreateItemResponseMessage", "m"));
        String success = createItemResponseMessageOmelement.getAttributeValue(new QName("ResponseClass"));
        // Assert if Create Message got successes
        Assert.assertEquals(success, "Success");
        //Extract Message Id and change Key
        itemId = (String) xPathEvaluate(omElement, "string(//t:ItemId/@Id)", namespaceMap);
        changeKey = (String) xPathEvaluate(omElement, "string(//t:ItemId/@ChangeKey)", namespaceMap);
        Assert.assertNotNull(itemId);
        Assert.assertNotNull(changeKey);
        //Sending CreateAttachment Request
        String createAttachmentOperation = getProxyServiceURL("createAttachmentOperation");
        FileInputStream fileInputStream = new FileInputStream(getESBResourceLocation() + File.separator + "config"
                + File.separator + "restRequests" + File.separator + "sampleRequest" + File.separator
                + "CreateAttachment.xml");
        OMElement createAttachmentSoapRequest = AXIOMUtil.stringToOM(IOUtils.toString(fileInputStream));
        OMElement parentItemID = createAttachmentSoapRequest.getFirstChildWithName(new QName("ParentItemId"));
        parentItemID.getFirstChildWithName(new QName("Id")).setText(itemId);
        parentItemID.getFirstChildWithName(new QName("ChangeKey")).setText(changeKey);
        HttpPost httpPost = new HttpPost(createAttachmentOperation);
        httpPost.setEntity(new StringEntity(createAttachmentSoapRequest.toString(), ContentType.TEXT_XML.withCharset
                (Charset.forName("UTF-8"))));
        HttpResponse response = defaultHttpClient.execute(httpPost);
        OMElement createAttachmentResponse = AXIOMUtil.stringToOM(IOUtils.toString(response.getEntity().getContent()));
        String createAttachmentStatus = createAttachmentResponse.getFirstElement().getFirstElement()
                .getAttributeValue(new QName("ResponseClass"));
        String attachmentId = (String) xPathEvaluate(createAttachmentResponse, "string(//t:AttachmentId/@Id)",
                namespaceMap);
        //Checked for the attachment creation got Success
        Assert.assertEquals(createAttachmentStatus, "Success");
        //Sending Get AttachmentRequest
        String getAttachmentProxyUrl = getProxyServiceURL("getAttachment");
        String payload = "<GetAttachment><AttachmentId>" + attachmentId + "</AttachmentId></GetAttachment>";
        HttpPost getAttachmentPost = new HttpPost(getAttachmentProxyUrl);
        getAttachmentPost.setEntity(new StringEntity(payload, ContentType.TEXT_XML.withCharset(Charset.forName
                ("UTF-8"))));
        HttpResponse getAttachmentPostHttpResponse = defaultHttpClient.execute(getAttachmentPost);
        OMElement getAttachmentOm = AXIOMUtil.stringToOM(IOUtils.toString(getAttachmentPostHttpResponse.getEntity()
                .getContent()));
        String GetAttachmentStatus = getAttachmentOm.getFirstElement().getFirstElement().getAttributeValue(new QName
                ("ResponseClass"));
        //Check for Getting attachment Response
        Assert.assertEquals(GetAttachmentStatus, "Success");
    }

    @Test(enabled = true, groups = {"wso2.esb"}, description = "EWS test case", dependsOnMethods = {"testCreateItem"})
    public void testFindItemAndSendItem() throws Exception {
        DefaultHttpClient defaultHttpClient = new DefaultHttpClient();
        //Send find Item Request
        String createItemProxyUrl = getProxyServiceURL("findItemOperation");
        RestResponse<OMElement> esbSoapResponse = sendXmlRestRequest(createItemProxyUrl, "POST",
                esbRequestHeadersMap, "FindItem.xml");
        OMElement omElement = esbSoapResponse.getBody();
        String findItemStatus = omElement.getFirstElement().getFirstElement().getAttributeValue(new QName
                ("ResponseClass"));
        //Check for success
        Assert.assertEquals(findItemStatus, "Success");
        //Extract Message unique Id and changeKey
        itemId = (String) xPathEvaluate(omElement, "string(//t:ItemId/@Id)", namespaceMap);
        changeKey = (String) xPathEvaluate(omElement, "string(//t:ItemId/@ChangeKey)", namespaceMap);
        Assert.assertNotNull(itemId);
        Assert.assertNotNull(changeKey);
        //Send SendItem Request
        String sendItemOperation = getProxyServiceURL("sendItem");
        HttpPost httpPost = new HttpPost(sendItemOperation);
        String payload = "<SendItem><SaveItemToFolder>true</SaveItemToFolder><ItemId><Id>" + itemId +
                "</Id><ChangeKey>" + changeKey + "</ChangeKey></ItemId></SendItem>";
        httpPost.setEntity(new StringEntity(payload, ContentType.TEXT_XML.withCharset(Charset.forName("UTF-8"))));
        HttpResponse response = defaultHttpClient.execute(httpPost);
        OMElement createAttachmentResponse = AXIOMUtil.stringToOM(IOUtils.toString(response.getEntity().getContent()));
        String GetAttachmentStatus = createAttachmentResponse.getFirstElement().getFirstElement().getAttributeValue
                (new QName("ResponseClass"));
        Assert.assertEquals(GetAttachmentStatus, "Success");
    }

    @Test(enabled = true, groups = {"wso2.esb"}, description = "EWS test case", dependsOnMethods =
            {"testFindItemAndSendItem"})
    public void testCreateUpdateDeleteItem() throws Exception {
        DefaultHttpClient defaultHttpClient = new DefaultHttpClient();
        String createItemProxyUrl = getProxyServiceURL("createItemOperation");
        RestResponse<OMElement> esbSoapResponse =
                sendXmlRestRequest(createItemProxyUrl, "POST", esbRequestHeadersMap, "CreateItem.xml");
        OMElement omElement = esbSoapResponse.getBody();
        OMElement createItemResponseMessageOmelement = omElement.getFirstElement().getFirstChildWithName(new QName
                ("http://schemas.microsoft.com/exchange/services/2006/messages", "CreateItemResponseMessage", "m"));
        String success = createItemResponseMessageOmelement.getAttributeValue(new QName("ResponseClass"));
        // Assert if Create Message got successes
        Assert.assertEquals(success, "Success");
        //Extract Message Id and change Key
        itemId = (String) xPathEvaluate(omElement, "string(//t:ItemId/@Id)", namespaceMap);
        changeKey = (String) xPathEvaluate(omElement, "string(//t:ItemId/@ChangeKey)", namespaceMap);
        Assert.assertNotNull(itemId);
        Assert.assertNotNull(changeKey);
        //Sending CreateAttachment Request
        String createAttachmentOperation = getProxyServiceURL("createAttachmentOperation");
        FileInputStream fileInputStream = new FileInputStream(getESBResourceLocation() + File.separator + "config"
                + File.separator + "restRequests" + File.separator + "sampleRequest" + File.separator
                + "CreateAttachment.xml");
        OMElement createAttachmentSoapRequest = AXIOMUtil.stringToOM(IOUtils.toString(fileInputStream));
        OMElement parentItemID = createAttachmentSoapRequest.getFirstChildWithName(new QName("ParentItemId"));
        parentItemID.getFirstChildWithName(new QName("Id")).setText(itemId);
        parentItemID.getFirstChildWithName(new QName("ChangeKey")).setText(changeKey);
        HttpPost httpPost = new HttpPost(createAttachmentOperation);
        httpPost.setEntity(new StringEntity(createAttachmentSoapRequest.toString(), ContentType.TEXT_XML.withCharset
                (Charset.forName("UTF-8"))));
        HttpResponse response = defaultHttpClient.execute(httpPost);
        OMElement createAttachmentResponse = AXIOMUtil.stringToOM(IOUtils.toString(response.getEntity().getContent()));
        String createAttachmentStatus = createAttachmentResponse.getFirstElement().getFirstElement()
                .getAttributeValue(new QName("ResponseClass"));
        String attachmentId = (String) xPathEvaluate(createAttachmentResponse, "string(//t:AttachmentId/@Id)",
                namespaceMap);
        //Checked for the attachment creation got Success
        Assert.assertEquals(createAttachmentStatus, "Success");

        //Send find Item Request
        String findItemOperation = getProxyServiceURL("findItemOperation");
        RestResponse<OMElement> findItemSoapResponse = sendXmlRestRequest(findItemOperation, "POST",
                esbRequestHeadersMap, "FindItem.xml");
        OMElement findItemSoapResponseOmElement = findItemSoapResponse.getBody();
        String findItemStatus = findItemSoapResponseOmElement.getFirstElement().getFirstElement().getAttributeValue
                (new QName("ResponseClass"));
        //Check for success
        Assert.assertEquals(findItemStatus, "Success");
        //Extract Message unique Id and changeKey
        itemId = (String) xPathEvaluate(findItemSoapResponseOmElement, "string(//t:ItemId/@Id)", namespaceMap);
        changeKey = (String) xPathEvaluate(findItemSoapResponseOmElement, "string(//t:ItemId/@ChangeKey)", namespaceMap);
        Assert.assertNotNull(itemId);
        Assert.assertNotNull(changeKey);
        //Do Update into Item
        String updateItemPayload = "<UpdateItem>\n" +
                "    <MessageDisposition>SaveOnly</MessageDisposition><ConflictResolution>AutoResolve" +
                "</ConflictResolution><ItemChanges><ItemChange><ItemId Id=\"" + itemId + "\" ChangeKey=\"" +
                changeKey + "\"/>" +
                "<Updates><AppendToItemField><FieldURI FieldURI=\"item:Body\"/><Message><Body BodyType=\"Text\">" +
                "Some additional text to append</Body></Message></AppendToItemField></Updates></ItemChange>" +
                "</ItemChanges></UpdateItem>";
        String updateItemProxyUrl = getProxyServiceURL("updateItemOperation");
        HttpPost updateItemPost = new HttpPost(updateItemProxyUrl);
        updateItemPost.setEntity(new StringEntity(updateItemPayload, ContentType.TEXT_XML.withCharset
                (Charset.forName("UTF-8"))));
        HttpResponse updateItemResponse = defaultHttpClient.execute(updateItemPost);
        OMElement updateItemResponsePayload = AXIOMUtil.stringToOM(IOUtils.toString(updateItemResponse.getEntity()
                .getContent()));
        String updateItemStatus = updateItemResponsePayload.getFirstElement().getFirstElement()
                .getAttributeValue(new QName("ResponseClass"));
        //Checked for the Update  got Success
        Assert.assertEquals(updateItemStatus, "Success");
        //Get updated Message and check for the update Exist
        String getItemProxyUrl = getProxyServiceURL("getItemOperation");
        String getItemPayload = "<GetMessage>\n" +
                "<BaseShape>Default</BaseShape><IncludeMimeContent>true</IncludeMimeContent>\t\n" +
                "<ItemId><Id>"+itemId+"</Id><ChangeKey>"+changeKey+"</ChangeKey>\n" +
                "</ItemId></GetMessage>\n";
        HttpPost getItemPost = new HttpPost(getItemProxyUrl);
        getItemPost.setEntity(new StringEntity(getItemPayload, ContentType.TEXT_XML.withCharset(Charset.forName
                ("UTF-8"))));
        OMElement getItemResponse = AXIOMUtil.stringToOM(IOUtils.toString(defaultHttpClient.execute(getItemPost)
                .getEntity().getContent()));
        String updatedBody = (String) xPathEvaluate(getItemResponse,"string(//t:Body/text())",namespaceMap);
        Assert.assertEquals(updatedBody,"Priority - Update specificationSome additional text to append");
        //Do Delete Request
        String deleteProxyUrl = getProxyServiceURL("deleteItemOperation");
        String deletePayload = "<DeleteItem><DeleteType>HardDelete</DeleteType><ItemId><Id>" + itemId +
                "</Id><ChangeKey>" + changeKey + "</ChangeKey></ItemId></DeleteItem>";
        HttpPost deleteItemPost = new HttpPost(deleteProxyUrl);
        deleteItemPost.setEntity(new StringEntity(deletePayload, ContentType.TEXT_XML.withCharset(Charset.forName
                ("UTF-8"))));
        OMElement deleteItemResponse = AXIOMUtil.stringToOM(IOUtils.toString(defaultHttpClient.execute(deleteItemPost)
                .getEntity().getContent()));
        String deleteItemStatus = deleteItemResponse.getFirstElement().getFirstElement().getAttributeValue(new QName
                ("ResponseClass"));
        Assert.assertEquals(deleteItemStatus, "Success");
        //Check weather Item and Attachment is deleted Successfully
        getItemPost.setEntity(new StringEntity(getItemPayload, ContentType.TEXT_XML.withCharset(Charset.forName
                ("UTF-8"))));
        OMElement getItemAfterDelete = AXIOMUtil.stringToOM(IOUtils.toString(defaultHttpClient.execute(getItemPost)
                .getEntity().getContent()));
        String getItemAfterDeleteStatus = getItemAfterDelete.getFirstElement().getFirstElement().getAttributeValue
                (new QName("ResponseClass"));
        Assert.assertEquals(getItemAfterDeleteStatus, "Error");
    }
}