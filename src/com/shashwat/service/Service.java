package com.shashwat.service;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JTextArea;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.shashwat.dao.DatabaseFunctions;
import com.shashwat.models.ClientLoggedIn;
import com.shashwat.models.LoginStatusModel;
import com.shashwat.models.RecieveMessageModel;
import com.shashwat.models.RegistryModel;
import com.shashwat.models.RegistryStatusModel;
import com.shashwat.models.SendMessageModel;
import com.shashwat.models.UserAccountModel;

public class Service {

	private static Service instance;
	private SocketIOServer server;
	private final int PORT_NUMBER = 9999;
	private JTextArea textArea;
	private List<ClientLoggedIn> clientList;					// to maintain a list of logged in clients on the server side
	private int clients = 0;
 	
	public static Service getService(JTextArea textArea) {
		if (instance == null) {
			instance = new Service(textArea);
		}
		return instance;
	}
	
	private Service(JTextArea textArea) {
		this.textArea = textArea;
		clientList = new ArrayList<>();
	}
	
	public void startService() {
		Configuration config = new Configuration();
		config.setPort(PORT_NUMBER);
		
		server = new SocketIOServer(config);
		
		server.addConnectListener(new ConnectListener() {
			
			@Override
			public void onConnect(SocketIOClient arg0) {
				textArea.append(">> client connected . . .\n");
			}
		});
		
		server.addDisconnectListener(new DisconnectListener() {
			
			@Override
			public void onDisconnect(SocketIOClient arg0) {
				textArea.append(">> client disconnected . . .\n");
			}
		});
		
		server.addEventListener("register", RegistryModel.class, new DataListener<RegistryModel>() {

			@Override
			public void onData(SocketIOClient arg0, RegistryModel arg1, AckRequest arg2) throws Exception {
				
				RegistryStatusModel statusModel = DatabaseFunctions.getDatabaseFunctions().checkAndInsert(arg1);
				arg2.sendAckData(statusModel);
				textArea.append("user : "+arg1.getUserName()+" password : "+arg1.getPassword()+"\n");
			}
			
		});
		
		server.addEventListener("login", RegistryModel.class, new DataListener<RegistryModel>() {
			
			@Override
			public void onData(SocketIOClient arg0, RegistryModel arg1, AckRequest arg2) throws Exception {
				
				LoginStatusModel statusModel = DatabaseFunctions.getDatabaseFunctions().validateLogin(arg1);
				arg2.sendAckData(statusModel); 
				textArea.append(arg1.getUserName()+" : login status : "+statusModel.getMessage()+"\n");
				UserAccountModel userAccount = new UserAccountModel(statusModel.getUserId(), arg1.getUserName(), true);
				addClient(userAccount, arg0);
				server.getBroadcastOperations().sendEvent("listUsers", userAccount);
			}
		});
		
		server.addEventListener("listUsers", Integer.class, new DataListener<Integer>() {

			@Override
			public void onData(SocketIOClient client, Integer data, AckRequest ackSender) throws Exception {
				
				List<UserAccountModel> users = DatabaseFunctions.getDatabaseFunctions().getUsers(data);
				client.sendEvent("listUsers", users.toArray());
			}
		});
		
		server.addEventListener("logout", Integer.class, new DataListener<Integer>() {
			
			@Override
			public void onData(SocketIOClient client, Integer data, AckRequest ackSender) throws Exception {
				boolean performedLogout = DatabaseFunctions.getDatabaseFunctions().logout(data);
				textArea.append("logout status: "+performedLogout+"\n");
				UserAccountModel userAccountModel = removeClient(client);
				server.getBroadcastOperations().sendEvent("updateUsersList", userAccountModel);
				ackSender.sendAckData("ok");
			}
		});
		
		server.addEventListener("updateUsersList", UserAccountModel.class, new DataListener<UserAccountModel>() {

			@Override
			public void onData(SocketIOClient client, UserAccountModel data, AckRequest ackSender) throws Exception {
				// TODO Auto-generated method stub
				client.sendEvent("updateUsersList", data);
			}
		});
		
		server.addEventListener("sendToUser", SendMessageModel.class, new DataListener<SendMessageModel>() {

			@Override
			public void onData(SocketIOClient client, SendMessageModel data, AckRequest ackSender) throws Exception {
				sendToClient(data);
				ackSender.sendAckData("sent");
			}
		});
		
		server.start();
		
		textArea.append(">> Server started on port number: "+PORT_NUMBER+" . . .\n");
		
	}
	
	private void addClient(UserAccountModel user, SocketIOClient client) {
		clientList.add(new ClientLoggedIn(user, client));
		textArea.append("number of clients : "+Integer.toString(++clients)+ "\n");
	}
	
	private UserAccountModel removeClient(SocketIOClient client) {
		UserAccountModel userAccountModel = null;
		for(ClientLoggedIn clientLoggedIn : clientList) {
			if(clientLoggedIn.getClient() == client) {
				userAccountModel = clientLoggedIn.getUserAccount();
				clientList.remove(clientLoggedIn);
				textArea.append("number of clients : "+Integer.toString(--clients)+ "\n");
				return userAccountModel;
			}
		}
		return userAccountModel;
	}
	
	private void sendToClient(SendMessageModel message) {
		for(ClientLoggedIn clientLoggedIn : clientList) {
			if(clientLoggedIn.getUserAccount().getUserId() == message.getToUserId()) {
				clientLoggedIn.getClient().sendEvent("recieveFromUser", new RecieveMessageModel(message.getFromUserID(), message.getTextMessage()));
				break;
			}
		}
	}

}


/* 							------NOTE------
 * The nature of Socket IO is that the sockets timeout (or in this case, DISCONNECTS) if there are no activities present in the socket. 
 * the efficient way is to use an HTTP API library to make calls to your server instead of socket.io,
 * using something like Retrofit, volley, or okHTTP. 
*/