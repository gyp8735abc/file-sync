//import com.alibaba.fastjson.JSON;
//import com.mzlion.easyokhttp.HttpClient;
//
//public class Test2 {
//
//	public static void main(String[] args) {
//		String loginRequestJson = String.format(
//				"{ \"jsonrpc\": \"2.0\", \"method\": \"user.login\", \"params\": { \"user\": \"%s\", \"password\": \"%s\" }, \"id\": 1 }", "Admin",
//				"zabbix");
//
//		String response = getResult(loginRequestJson);
//		String auth = JSON.parseObject(response).getString("result");
//		String getAlertRequestJson = String.format(
//				"{ \"jsonrpc\": \"2.0\", \"method\": \"alert.get\", \"params\": { \"output\": \"extend\", \"status\": 2}, \"auth\": \"%s\", \"id\": 1 }", auth);
//		response = getResult(getAlertRequestJson);
//		System.out.println(response);
//		/**
//		 * <pre>
//		 * alertid	string	ID of the alert.
//		 * actionid	string	ID of the action that generated the alert.
//		 * alerttype	integer	Alert type. 
//		 * 
//		 * Possible values: 
//		 * 0 - message; 
//		 * 1 - remote command.
//		 * clock	timestamp	Time when the alert was generated.
//		 * error	string	Error text if there are problems sending a message or running a command.
//		 * esc_step	integer	Action escalation step during which the alert was generated.
//		 * eventid	string	ID of the event that triggered the action.
//		 * mediatypeid	string	ID of the media type that was used to send the message.
//		 * message	text	Message text. Used for message alerts.
//		 * retries	integer	Number of times Zabbix tried to send the message.
//		 * sendto	string	Address, user name or other identifier of the recipient. Used for message alerts.
//		 * status	integer	Status indicating whether the action operation has been executed successfully. 
//		 * 
//		 * Possible values for message alerts: 
//		 * 0 - message not sent; 
//		 * 1 - message sent; 
//		 * 2 - failed after a number of retries. 
//		 * 
//		 * Possible values for command alerts: 
//		 * 1 - command run; 
//		 * 2 - tried to run the command on the Zabbix agent but it was unavailable.
//		 * subject	string	Message subject. Used for message alerts.
//		 * userid	string	ID of the user that the message was sent to.
//		 * 
//		 * 
//		 * {
//		 *     "jsonrpc": "2.0",
//		 *     "result": [
//		 *         {
//		 *             "alertid": "1",
//		 *             "actionid": "3",
//		 *             "eventid": "21243",
//		 *             "userid": "1",
//		 *             "clock": "1362128008",
//		 *             "mediatypeid": "1",
//		 *             "sendto": "support@company.com",
//		 *             "subject": "PROBLEM: Zabbix agent on Linux server is unreachable for 5 minutes: ",
//		 *             "message": "Trigger: Zabbix agent on Linux server is unreachable for 5 minutes: \nTrigger status: PROBLEM\nTrigger severity: Not classified",
//		 *             "status": "0",
//		 *             "retries": "3",
//		 *             "error": "",
//		 *             "esc_step": "1",
//		 *             "alerttype": "0"
//		 *         }
//		 *     ],
//		 *     "id": 1
//		 * }
//		 * </pre>
//		 */
//	}
//
//	private static String getResult(String request) {
//		return HttpClient.textBody("http://10.65.66.223/zabbix/api_jsonrpc.php").header("Content-Type", "application/json-rpc").charset("UTF-8")
//				.json(request).execute().asString();
//	}
//}
