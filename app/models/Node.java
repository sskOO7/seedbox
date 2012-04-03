package models;

import com.jcraft.jsch.*;
import java.io.*;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import play.Logger;
import play.modules.siena.EnhancedModel;
import siena.Column;
import siena.Generator;
import siena.Id;
import siena.Model;
import siena.Table;

@Table("node")
public class Node extends EnhancedModel {
	
	@Id(Generator.AUTO_INCREMENT)
	public Long id;

	@Column("name")
	public String name;
	
	@Column("ip_address")
	public String ipAddress;
	
	@Column("username")
	public String username;
	
	@Column("password")
	public String password;
	
	@Column("currently_populating_database")
	public Boolean currentlyPopulatingDatabase = false;
	
	public transient String uptime;
	
	private Transmission transmission;

	public String getUptime() {
		return executeSsh("uptime").substring(0, 25);
	}
	
	public String getFreeSpace() {
		return executeSsh("df -h | grep /dev/ | awk '{print $2}'");
	}
	
	public int getUserCount() {
		return User.all().filter("node", this).count();
	}
	
	public Transmission getTransmission() {
		return Model.all(Transmission.class).getByKey(transmission.id);
	}

	@Override
	public String toString() {
		return name;
	}

	public String executeSsh(String command) {
		try {
			ChannelExec c = getExecChannel();
			c.setCommand(command);
			c.connect();
			String result = convertStreamToString(c.getInputStream());
			c.disconnect();
			c.getSession().disconnect();
			return result;
		} catch (JSchException ex) {
			Logger.error(ex, "Unable to execute SSH");
			return ex.toString();			
		} catch (IOException ex) {
			Logger.error(ex, "Unable to execute SSH");
			return ex.toString();
		}
	}

	public String readFile(String filename) throws JSchException, SftpException {
		ChannelSftp sftp = this.getSftpChannel();
		sftp.connect();
		InputStream is = sftp.get(filename);
		String str = convertStreamToString(is);
		sftp.disconnect();
		sftp.getSession().disconnect();
		return str;
	}

	public void writeFile(String contents, String filename) throws JSchException, SftpException {
		ChannelSftp sftp = this.getSftpChannel();
		sftp.connect();
		try {
			InputStream is = new ByteArrayInputStream(contents.getBytes("UTF-8"));
			sftp.put(is, filename);
			sftp.disconnect();
			sftp.getSession().disconnect();
		} catch (UnsupportedEncodingException ex) {
			Logger.error(ex, "");
		}
	}

	private ChannelSftp getSftpChannel() throws JSchException {
		Session s = this.getSshSession();
		return (ChannelSftp) s.openChannel("sftp");
	}

	private ChannelExec getExecChannel() throws JSchException {
		Session s = this.getSshSession();
		return (ChannelExec) s.openChannel("exec");
	}

	private Session getSshSession() throws JSchException {
		JSch j = new JSch();
		Session s = j.getSession(this.username, this.ipAddress);
		s.setPassword(this.password);
		s.setConfig("StrictHostKeyChecking", "no");
		s.connect();
		return s;
	}
	
	private void disconnectActiveSession() {
		/*
		if (_session != null) {
			_session.disconnect();
		}*/
	}

	private String convertStreamToString(InputStream is) {
		try {
			return new Scanner(is).useDelimiter("\\A").next().trim();
		} catch (NoSuchElementException e) {
			return "";
		}
	}
}
