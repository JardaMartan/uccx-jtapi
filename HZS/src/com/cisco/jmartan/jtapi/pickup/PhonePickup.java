package com.cisco.jmartan.jtapi.pickup;

import javax.telephony.Address;
import javax.telephony.CallObserver;
import javax.telephony.InvalidStateException;
import javax.telephony.JtapiPeerFactory;
import javax.telephony.TerminalObserver;
import javax.telephony.events.CallEv;
import javax.telephony.events.ProvEv;
import javax.telephony.events.ProvInServiceEv;
import javax.telephony.events.ProvShutdownEv;
import javax.telephony.events.TermEv;

import com.cisco.cti.util.Condition;
import com.cisco.jtapi.extensions.CiscoJtapiPeer;
import com.cisco.jtapi.extensions.CiscoJtapiVersion;
import com.cisco.jtapi.extensions.CiscoProvider;
import com.cisco.jtapi.extensions.CiscoProviderObserver;
import com.cisco.jtapi.extensions.CiscoTerminal;

public class PhonePickup implements CiscoProviderObserver {
	private Condition conditionInService = new Condition();
	private CiscoProvider provider;

	public PhonePickup(String cucmAddr, String username, String password) {
		System.out.println("phonepickup: " + new CiscoJtapiVersion());
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
			provider = (CiscoProvider) peer.getProvider(providerString);
			provider.addObserver(this);
			conditionInService.waitTrue();
			System.out.println("Provider in service.");

		} catch (Exception e) {
			System.out.println("Caught exception " + e);
		}
	}

	// Shut down the provider
	public void shutdown() {
		this.provider.shutdown();
	}

	public String pickup(String deviceName) {
		String result = null;
		try {
			CiscoTerminal ct = (CiscoTerminal) this.provider
					.getTerminal(deviceName);
			ct.addObserver(new TerminalObserver() {

				@Override
				public void terminalChangedEvent(TermEv[] arg0) {
					// TODO Auto-generated method stub

				}
			});

			System.out.println("Terminal " + deviceName + ": " + ct.getState()
					+ " : " + CiscoTerminal.IN_SERVICE);

			Address[] addrList = ct.getAddresses();

			int i = 0;
			while (i < addrList.length) {
				System.out.println("Addr: " + addrList[i].toString());
				try {
					addrList[i].addCallObserver(new CallObserver() {

						@Override
						public void callChangedEvent(CallEv[] arg0) {
							// TODO Auto-generated method stub

						}
					});
					ct.pickup(addrList[i]);
					result = addrList[i].getName();
					break;
				} catch (InvalidStateException ise) {
					System.out.println(String.format("pickup: %s",
							ise.getMessage()));
				} finally {
					i++;
				}
			}
		} catch (Exception x) {
			System.out.println(String.format("pickup exception: %s",
					x.getMessage()));
		}

		return result;
	}

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

	public static void main(String[] args) {
		PhonePickup p = new PhonePickup(args[0], args[1], args[2]);
		String pickupResult = p.pickup(args[3]);
		if (pickupResult != null)
			System.out.println("Pickup result: " + pickupResult.toString());
		p.shutdown();
		System.exit(0);
	}

}
