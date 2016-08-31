package com.cisco.jmartan.jtapi.hzs;

import java.util.HashSet;

import javax.telephony.Address;
import javax.telephony.Call;
import javax.telephony.CallObserver;
import javax.telephony.Connection;
import javax.telephony.InvalidArgumentException;
import javax.telephony.InvalidPartyException;
import javax.telephony.InvalidStateException;
import javax.telephony.JtapiPeerFactory;
import javax.telephony.MethodNotSupportedException;
import javax.telephony.PrivilegeViolationException;
import javax.telephony.ResourceUnavailableException;
import javax.telephony.Terminal;
import javax.telephony.TerminalConnection;
import javax.telephony.TerminalObserver;
import javax.telephony.callcontrol.CallControlCall;
import javax.telephony.events.CallEv;
import javax.telephony.events.ProvEv;
import javax.telephony.events.ProvInServiceEv;
import javax.telephony.events.ProvShutdownEv;
import javax.telephony.events.TermEv;

import com.cisco.cti.util.Condition;
import com.cisco.jtapi.extensions.CiscoCall;
import com.cisco.jtapi.extensions.CiscoJtapiPeer;
import com.cisco.jtapi.extensions.CiscoJtapiVersion;
import com.cisco.jtapi.extensions.CiscoPartyInfo;
import com.cisco.jtapi.extensions.CiscoProvider;
import com.cisco.jtapi.extensions.CiscoProviderObserver;
import com.cisco.jtapi.extensions.CiscoTerminal;
import com.cisco.jtapi.extensions.CiscoTerminalConnection;

public class PhoneControl implements CiscoProviderObserver {
	private Condition conditionInService = new Condition();
	private CiscoProvider provider;

	public PhoneControl(String cucmAddr, String username, String password) {
		System.out.println(this.getClass().getName() + " "
				+ new CiscoJtapiVersion());
		try {
			System.out.println("Initializing Jtapi");

// change to CUCM IP/hostname, JTAPI username & password
			String providerName = cucmAddr;
			String login = username;
			String passwd = password;
			CiscoJtapiPeer peer = (CiscoJtapiPeer) JtapiPeerFactory
					.getJtapiPeer(null);
			String providerString = providerName + ";login=" + login
					+ ";passwd=" + passwd;
			System.out.println("Opening: " + providerString);
			this.provider = (CiscoProvider) peer.getProvider(providerString);
			this.provider.addObserver(this);
			conditionInService.waitTrue();

// Start dummy observers on all terminals and addresses. Without this no actions
// are possible.
			Terminal[] ctArr = this.provider.getTerminals();
			if (ctArr != null) {
				for (Terminal t : ctArr) {
					t.addObserver(new TerminalObserver() {

						@Override
						public void terminalChangedEvent(TermEv[] arg0) {
							// TODO Auto-generated method stub

						}
					});
				}
			}

			Address[] addrArr = this.provider.getAddresses();
			if (addrArr != null) {
				for (Address a : addrArr) {
					a.addCallObserver(new CallObserver() {

						@Override
						public void callChangedEvent(CallEv[] arg0) {
							// TODO Auto-generated method stub

						}
					});

				}
			}

			System.out.println("Provider in service.");

		} catch (Exception e) {
			System.out.println("Caught exception " + e);
		}
	}

// Shut down the provider
	public void shutdown() {
		this.provider.shutdown();
	}

// Pickup on the deviceName. Longest ringing call in the device lines' pickup
// group is selected.
// The line is selected sequentially top down. First success ends the attempts.
	public String pickup(String deviceName) {
		String result = null;
		try {
			CiscoTerminal ct = (CiscoTerminal) this.provider
					.getTerminal(deviceName);

			System.out.println("Terminal " + deviceName + ": " + ct.getState()
					+ " : " + CiscoTerminal.IN_SERVICE);

			Address[] addrArr = ct.getAddresses();
			if (addrArr != null) {
				for (Address a : addrArr) {
					try {
						System.out.println("Addr: " + a.toString());
						ct.pickup(a);
						result = a.getName();
						break;
					} catch (InvalidStateException ise) {
						System.out.println(String.format("pickup: %s",
								ise.getMessage()));
					}
				}
			}

		} catch (Exception x) {
			System.out.println(String.format("pickup exception: %s",
					x.getMessage()));
		}

		return result;
	}

// Send XML document to the phone 'deviceName'
	public void sendData(String deviceName, String phoneXML) {
		try {
			CiscoTerminal phone = (CiscoTerminal) this.provider
					.getTerminal(deviceName);
			System.out.println("Send data to device: " + deviceName);
			phone.sendData(phoneXML.getBytes());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

// Thread class for initiating simultaneous sendData()
	private class SendDataThread implements Runnable {

		private String deviceName;
		private String phoneXML;

		public SendDataThread(String deviceName, String phoneXML) {
			super();
			this.deviceName = deviceName;
			this.phoneXML = phoneXML;
		}

		@Override
		public void run() {
			sendData(this.deviceName, this.phoneXML);
		}

	}

// simultaneous sendData() to the array of deviceNames.
	public void sendData(String[] deviceNameArr, String phoneXML) {
		for (String deviceName : deviceNameArr) {
			Thread t = new Thread(new SendDataThread(deviceName, phoneXML));
			t.start();
		}
	}

// sendData() to a device(s) with the DN of 'address'
	public void sendDataToAddress(String address, String phoneXML) {
		String[] addrArr = { address };
		this.sendDataToAddress(addrArr, phoneXML);
	}

// sendData() to all device where the DNs from address array are configured.
// Duplicities are removed.
	public void sendDataToAddress(String[] addrArr, String phoneXML) {
		HashSet<String> tSet = new HashSet<String>();
		for (String addr : addrArr) {
			try {
				Terminal[] ta = this.provider.getAddress(addr).getTerminals();
				if (ta != null) {
					for (Terminal t : ta) {
						tSet.add(t.getName());
					}
				}
			} catch (InvalidArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		String[] deviceArr = tSet.toArray(new String[tSet.size()]);
		this.sendData(deviceArr, phoneXML);
	}

// sendData() to all devices owned by the JTAPI user.
	public void sendDataToAll(String phoneXML) {
		try {
			Terminal[] ta = this.provider.getTerminals();
			if (ta != null) {
				HashSet<String> tSet = new HashSet<String>();
				for (Terminal t : ta) {
					tSet.add(t.getName());
				}
				String[] deviceArr = tSet.toArray(new String[tSet.size()]);
				this.sendData(deviceArr, phoneXML);
			}
		} catch (ResourceUnavailableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private CallControlCall findActiveCall(String deviceName,
			Boolean setTransferController) {
		CallControlCall activeCall = null;
		try {
			CiscoTerminal phone = (CiscoTerminal) this.provider
					.getTerminal(deviceName);
			Address[] addrList = phone.getAddresses();
			for (int i = 0; i < addrList.length; i++) {
				Connection[] connectionList = addrList[i].getConnections();
				if (connectionList != null) {
					for (int j = 0; j < connectionList.length; j++) {
						TerminalConnection[] termConList = connectionList[j]
								.getTerminalConnections();
						if (termConList != null) {
							for (int k = 0; k < termConList.length; k++) {
								if (termConList[k].getTerminal().getName()
										.equalsIgnoreCase(deviceName)) {
									int state = ((CiscoTerminalConnection) termConList[k])
											.getCallControlState();
									System.out
											.println("Connection "
													+ ((CiscoTerminalConnection) termConList[k])
															.toString()
													+ " is in state " + state);
									if (state == CiscoTerminalConnection.TALKING) {
										System.out
												.println("Found TALKING call "
														+ ((CiscoTerminalConnection) termConList[k])
																.toString()
														+ " is in state "
														+ state);
										activeCall = (CallControlCall) termConList[k]
												.getConnection().getCall();
										if (setTransferController)
											activeCall
													.setTransferController(termConList[k]);
										return activeCall;
									}
								}
							}
						}
					}
				}
			}
		} catch (InvalidArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MethodNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ResourceUnavailableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return activeCall;
	}

	public CallerInfo findCallerName(String callingPartyNumber,
			String calledPartyNumber, String originalCalledPartyNumber) {
		CiscoCall callDetails = this.findCall(callingPartyNumber,
				calledPartyNumber, originalCalledPartyNumber);
		if (callDetails != null) {
			CallerInfo result = new CallerInfo(callDetails
					.getCurrentCallingPartyInfo().getDisplayName(),
					callDetails.getCurrentCallingPartyUnicodeDisplayName());
			System.out.println("Caller name: " + result.displayName
					+ ", unicode: " + result.displayNameUnicode);
			return result;
		} else {
			System.out.println("Caller name unknown.");
			return new CallerInfo("Neznamy", "Neznámý");
		}
	}
	
	public CallerInfo getCallerName(String callId) {
		CiscoCall callDetails = this.getCall(callId);
		if (callDetails != null) {
			CallerInfo result = new CallerInfo(callDetails
					.getCurrentCallingPartyInfo().getDisplayName(),
					callDetails.getCurrentCallingPartyUnicodeDisplayName());
			System.out.println("Caller name: " + result.displayName
					+ ", unicode: " + result.displayNameUnicode);
			return result;
		} else {
			System.out.println("Caller name unknown.");
			return new CallerInfo("Neznamy", "Neznámý");
		}
	}

	public CiscoCall findCall(String callingPartyNumber,
			String calledPartyNumber, String originalCalledPartyNumber) {
		CiscoCall foundCall = null;

		// get list of active calls on provider, iterate through to find the
// match
		System.out.println("Searching for calling: " + callingPartyNumber
				+ ", called: " + calledPartyNumber + ", orig. called: "
				+ originalCalledPartyNumber);
		try {
			Call[] callList = this.provider.getCalls();
			if (callList != null) {
				System.out.println("Found " + callList.length + " call(s).");
				for (int i = 0; i < callList.length; i++) {
					CiscoPartyInfo callingParty = ((CiscoCall) callList[i])
							.getCurrentCallingPartyInfo();
					CiscoPartyInfo calledParty = ((CiscoCall) callList[i])
							.getCurrentCalledPartyInfo();
					CiscoPartyInfo originalCalledParty = ((CiscoCall) callList[i])
							.getCalledPartyInfo();
					System.out.println("Checking call id: "
							+ ((CiscoCall) callList[i]).getCallID().toString()
							+ ", calling: "
							+ callingParty.getAddress().getName()
							+ ", called: " + calledParty.getAddress().getName()
							+ ", orig. called: "
							+ originalCalledParty.getAddress().getName());
					if (callingParty.getAddress().getName()
							.equals(callingPartyNumber)) {
						System.out
								.println("Calling party "
										+ callingPartyNumber
										+ " found. Checking original called party info.");
						if (originalCalledParty.getAddress().getName()
								.equals(originalCalledPartyNumber)) {
							System.out.println("Original called party "
									+ originalCalledPartyNumber
									+ " OK. Checks completed, call "
									+ ((CiscoCall) callList[i]).getCallID()
											.toString()
									+ " found, connected on CTI Port: "
									+ calledParty.getAddress().getName());
							foundCall = (CiscoCall) callList[i];
							break;
						} else {
							System.out.println("Original called party "
									+ originalCalledPartyNumber
									+ " doesn't match "
									+ originalCalledPartyNumber);
						}
					}
				}
			} else {
				System.out.println("No active calls found on provider.");
			}
		} catch (ResourceUnavailableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return foundCall;
	}

	public CiscoCall getCall(String callId) {
		CiscoCall foundCall = null;
		try {
			Call[] callList = this.provider.getCalls();
			if (callList != null) {
				for (int i = 0; i < callList.length; i++) {
					System.out.println("Checking call: "
							+ ((CiscoCall) callList[i]).getCallID().toString());
					if (((CiscoCall) callList[i]).getCallID().toString()
							.equals(callId)) {
						foundCall = (CiscoCall) callList[i];
						System.out.println("Call: " + callId + " found");
						break;
					}
				}
			}
		} catch (ResourceUnavailableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return foundCall;
	}

// transfer() an active call on the device to a number
	public void transfer(String deviceName, String destination) {

// algorithm:
// 1. get list of calls on all the lines of the device
// 2. select the active call
		CallControlCall activeCall = this.findActiveCall(deviceName, true);
		if (activeCall != null) {
			try {
				activeCall.transfer(destination);
			} catch (InvalidArgumentException | InvalidStateException
					| InvalidPartyException | MethodNotSupportedException
					| PrivilegeViolationException
					| ResourceUnavailableException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
// active call not found dial a new call
			this.dial(deviceName, destination);
		}
		System.out.println("Send data to device: " + deviceName);
// 3. use CallControlCall.transfer(String address)
	}

// wait for the JTAPI provider to fully initiate
	@Override
	public void providerChangedEvent(ProvEv[] eventList) {
		if (eventList != null) {
			for (int i = 0; i < eventList.length; i++) {
				if (eventList[i] instanceof ProvInServiceEv) {
					conditionInService.set();
				} else if (eventList[i] instanceof ProvShutdownEv) {
// shutdown complete
				}
			}
		}
	}

	public void dial(String deviceName, String destination) {
		this.sendData(
				deviceName,
				"<CiscoIPPhoneExecute><ExecuteItem Priority=\"0\" URL=\"Init:Services\" /><ExecuteItem Priority=\"0\" URL=\"Dial:"
						+ destination + "\" /></CiscoIPPhoneExecute>");
	}

// a test procedure
	public static void main(String[] args) {
		PhoneControl p = new PhoneControl(args[0], args[1], args[2]);
/*
 * String xml =
 * "<CiscoIPPhoneExecute><ExecuteItem Priority=\"0\" URL=\"http://192.168.21.91:9080/queue-status?csq=road&amp;devicename=#DEVICENAME#\"/>"
 * + "<ExecuteItem Priority=\"0\" URL=\"Play:ClockShop.raw\"/>"
 * + "</CiscoIPPhoneExecute>";
 * 
 * xml =
 * "<CiscoIPPhoneStatusFile><Text>Fronta: 10   Vola: 602123456</Text><Timer>0</Timer><LocationX>0</LocationX><LocationY>0</LocationY><URL>http://192.168.21.85/ksp/headers.php</URL></CiscoIPPhoneStatusFile>"
 * ;
 * p.sendDataToAddress("1101", xml);
 */
// p.transfer("SEPBCC49351BBC4", "1103");
		p.findCallerName("1100", "888816220", "888816220");
		p.shutdown();
		System.exit(0);
	}

}
