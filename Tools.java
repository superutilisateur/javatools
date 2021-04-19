/*
 * Copyright (c) 2012 Mikaël GUILLEMOT
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Cette classe fournie des outils de base pour de petites applications.
 * L'objectif est de proposer un acces simple et rapide pour :<br/>
 * <br/>
 * 
 * - la gestion des properties<br/>
 * - les logs*<br/>
 * - la supression de repertoire en cascade<br/>
 * - la recuperation d'une arboressence<br/>
 * - la modification (replace) dans un fichier<br/>
 * - la recuperation d'informations divers<br/>
 * <br/>
 * 
 * Comportement:<br/>
 * La recuperation d'une instance de cette classe peut se faire en precisant le
 * fichier de configuration a utiliser, cepandant une fois le classe cree ce
 * parrametre n'est plus modifiable.<br/>
 * <br/>
 * 
 * En cas de non existance du fichier de configuration il est genere
 * automatiquement. Une generation externe peut etre appele en utillisant
 * l'interface PropertiesGenerator.<br/>
 * On doit faire un setPropertiesGenerator(null) pour ne pas utiliser de fichier
 * de configuration. <br/>
 * 
 * *Lors de l'utillisation du log la methode closeLog() doit etre appelee a la
 * fin du programme pour eviter les pertes de donnees.<br/>
 * <br/>
 * 
 * derniere mise a jour : 09 janvier 2011 <br/>
 * 
 * @author Mikael Guillemot
 * @version 3.35a
 * 
 *          changement : 3.35a : correction du template en (date)+reformatage
 *          3.35 : (4 novembre 2011)
 * */

public final class Tools {

	private static String conf_path = "configuration.properties";
	private static boolean is_conf_path_unset = true;

	private static PropertiesGenerator conf_generator = null;
	private static boolean is_generator_unset = true;

	private static Tools instance = null;

	private Properties conf = null;
	public static String lineSeparator = System.getProperty("line.separator");

	/* configuration du log */
	private boolean file_log = false;
	private boolean console_log = true;
	private DateFormat timeStamp = null;
	private boolean timestamp_active = false;
	private RandomAccessFile logfile = null;
	private int log_entry = 0;
	private int log_date = 0;

	// experimental : pour permetre de loger en plusieur fois
	private boolean skipTimeStamp = false;

	/**
	 * interface utilise par la classe Tools pour generer un fichier de
	 * configuration par defaut.
	 */
	public interface PropertiesGenerator {

		/**
		 * genere le fichier de properties du nom de fileName
		 * 
		 * @param fileName
		 * @return false si une erreur s'est produite true sinon.
		 */
		public boolean generate(String fileName);
	}

	/* classe de generation par defaut */
	private final class AutoGen implements PropertiesGenerator {

		/*
		 * (non-Javadoc)
		 * 
		 * @see tools.Tools.PropertiesGenerator#generate(java.lang.String)
		 */
		@Override
		public final boolean generate(String fileName) {
			try {
				File f = new File(fileName);
				f.createNewFile();
				RandomAccessFile raf = new RandomAccessFile(f, "rw");
				raf.writeBytes("#default properties file:" + Tools.lineSeparator);
				raf.writeBytes("LOG_ACTIVE = false" + Tools.lineSeparator);
				raf.writeBytes("TIMESTAMP_ACTIVE = false" + Tools.lineSeparator);
				raf.writeBytes("SILENT_LOG = false" + Tools.lineSeparator);
				raf.writeBytes("LOG_PATH = ./log/" + Tools.lineSeparator);
				raf.writeBytes("CLEAR_LOG = false" + Tools.lineSeparator);
				raf.close();
			} catch (IOException e) {
				return false;
			}
			return true;
		}
	}

	/**
	 * permet de definir in generateur de configuration. Cette methode est a
	 * appeler avant de recupperer l'instance de Tools.
	 * 
	 * @param generator
	 */
	public final synchronized static void _setPropertiesGenerator(PropertiesGenerator generator) {
		if (is_generator_unset) {
			conf_generator = generator;
			is_generator_unset = false;
		}
	}

	/**
	 * permet de definir le fichier de configuration a utiliser. Cette methode
	 * est a appeler avant de recupperer l'instance de Tools.
	 * 
	 * Par defaut, le fichier configuration.properties du repertoir d'execution
	 * est utilisé.
	 * 
	 * @param generator
	 */
	public final synchronized static void _setPropertiesFile(String configFileName) {
		if (is_conf_path_unset) {
			conf_path = configFileName;
			is_conf_path_unset = false;
		}
	}

	/**
	 * methode pour recuperer l'instance de la classe
	 */
	public final synchronized static Tools getInstance() {
		if (instance == null) {
			is_generator_unset = false;
			is_conf_path_unset = false;
			instance = new Tools();
		}
		return instance;
	}

	/* constructeur prive du singleton */
	private Tools() {
		timeStamp = new SimpleDateFormat("{hh:mm:ss} ");
		if (lineSeparator == null)
			lineSeparator = "\n";
		init_properties();
		if (this.getBooleanFor("TIMESTAMP_ACTIVE"))
			timestamp_active = true;
		if (this.getBooleanFor("SILENT_LOG"))
			console_log = false;
		if (this.getBooleanFor("LOG_ACTIVE"))
			init_log(this.getStringFor("LOG_PATH"));
	}

	/* recupere la configuration.properties */
	private final void init_properties() {
		conf = new Properties();
		try {
			File f = new File(conf_path);
			if (!f.exists()) {
				if (is_conf_path_unset)
					conf_generator = new AutoGen();
				if (conf_generator != null)
					conf_generator.generate(conf_path);
				// si conf_generator est null c'est l'utilisateur qui le demande
				else
					return;
			}
			FileInputStream file = null;
			file = new FileInputStream(conf_path);
			conf.load(file);
			file.close();
		} catch (IOException e) {
			logError("Can't find (or open) \"" + conf_path + "\"");

		}
	}

	/* intialise le loggeur en utilisant la configuration du fichier properties */
	private final void init_log(String log_path) {
		Date currentDate = new Date();
		DateFormat dateFormat = new SimpleDateFormat("ddMMyyhhmmss");
		log_date = Integer.parseInt(new SimpleDateFormat("ddMMyy").format(currentDate));
		File path = new File(log_path);
		try {
			// si le repertoire n'exite pas on le cree
			if (!path.exists())
				path.mkdir();
			// on vide le repertoire de log avant de cree le nouveau log
			if (this.getBooleanFor("CLEAR_LOG_PATH")) {
				log("cleaning " + path.getAbsolutePath());
				if(!this.cleanDir(log_path))
					logError("Fail to clean log dir");
			}

			logfile = new RandomAccessFile(new File(log_path + "log_" + dateFormat.format(currentDate) + ".log"), "rw");
			file_log = true;
		} catch (IOException e) {

			logError("Can't create " + path.getAbsolutePath() + "\\log_" + dateFormat.format(currentDate) + ".log");
			logWarning("Log function disabled!");
		}
	}

	/**
	 * ajoute un avertissement dans le log
	 * 
	 * @param msg
	 */
	public final void logWarning(String msg) {
		log(new StringBuilder("[WARNING]").append(msg).toString());
	}

	/**
	 * ajoute un avertissement dans le log
	 * 
	 * @param msg
	 */
	public final void logError(String msg) {
		log(new StringBuilder("[ERROR]").append(msg).toString());
	}

	/**
	 * ajoute un message au log et passe une ligne
	 * 
	 * @param msg
	 */
	public synchronized final void log(String msg) {
		log(msg, true);
	}

	/**
	 * ajoute un message au log en precisant l'ajout d'une nouvelle ligne ou non
	 * (si non le time stamp est desactivé
	 * 
	 * @param msg
	 * @param newline
	 */
	public synchronized final void log(String msg, boolean newline) {
		StringBuilder tmp = new StringBuilder();
		Date current_date = new Date();
		if (timestamp_active && !skipTimeStamp)
			tmp.append(timeStamp.format(current_date));
		tmp.append(msg);
		skipTimeStamp = false;
		if (newline)
			tmp.append(lineSeparator);
		else
			skipTimeStamp = true;

		String out = tmp.toString();
		// on a le message on decide d'ou on le log
		if (file_log)
			try {
				int date = Integer.parseInt(new SimpleDateFormat("ddMMyy").format(current_date));
				if (log_entry > 1000 || date != log_date) {
					log_entry = 0;
					this.closeLog();
					init_log(this.getStringFor("LOG_PATH"));
				}
				logfile.writeBytes(out);
				log_entry++;
			} catch (IOException e) {
				// en cas d'erreur sur le fichier de log on fonctionne en mode
				// degrade : la console.
				file_log = false;
				console_log = true;
				logError("Log writing : " + e.getLocalizedMessage());
				logWarning("File Log function disabled!");
				this.closeLog();
			}
		if (console_log)
			System.out.print(out);
	}

	/**
	 * ferme le log
	 */
	public synchronized final void closeLog() {
		if (logfile != null) {
			try {
				logfile.close();
				logfile = null;
			} catch (Exception e) {
				file_log = false;
				console_log = true;
				logError("Closing log : " + e.getLocalizedMessage());
				logWarning("File Log function disabled!");
			}
		}
	}

	/**
	 * Renvoi la chaine corespondant au label (clef) dans le fichier de config
	 * 
	 * @param label
	 * @return
	 */
	public synchronized final String getStringFor(String label) {
		String tmp = conf.getProperty(label);
		if (tmp != null) {
			return tmp;
		}
		logError("Configuration: Can't read \"" + label + "\" key!");
		return null;
	}

	/**
	 * Renvoi l'entier correspondant au label (clef) dans le fichier de config,
	 * -1 en cas d'erreur
	 * 
	 * @param label
	 * @return
	 */
	public synchronized final int getIntFor(String label) {
		try {
			return Integer.parseInt(getStringFor(label));
		} catch (NumberFormatException ne) {
			logError("Configuration: \"" + label + "\" has wrong value!");
		}
		return -1;
	}

	/**
	 * Renvoi true si la cle exite dans le fichier de config et qu'elle est a
	 * true, false dans tout les autre cas!
	 * 
	 * @param label
	 * @return
	 */
	public synchronized final boolean getBooleanFor(String label) {
		return Boolean.parseBoolean(conf.getProperty(label));
	}

	/**
	 * renvoi le separateur de ligne par defaut
	 */
	public final String getLS() {
		return lineSeparator;
	}

	/**
	 * effectue une copie de fichier
	 * 
	 * @param origin
	 * @param copy
	 * @return true si l'opperation est une reussite false sinon.
	 */
	public final boolean FileCopy(File origin, File copy) {
		FileChannel in = null;
		FileChannel out = null;
		boolean succes = false;

		try {
			in = new FileInputStream(origin).getChannel();
			out = new FileOutputStream(copy).getChannel();
			in.transferTo(0, in.size(), out);
			succes = true;
		} catch (IOException e) {
			this.logError("copy from " + origin.getName() + " to " + copy.getName() + " failed: "
					+ e.getLocalizedMessage());
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
				}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
				}
			}
		}
		return succes;
	}

	/**
	 * Dresse la liste des fichiers (non cache) present dans le repertoir passe
	 * en parametre
	 * 
	 * @param path
	 *            le repertoir a parcourir
	 * @param fileList
	 *            la liste des fichier a construire
	 */
	public final void getFilesIn(String path, List<File> fileList) {
		File dir = new File(path);
		if (!dir.exists() || !dir.canRead() || !dir.isDirectory())
			return;

		File sub[] = dir.listFiles();
		for (File f : sub) {
			if (!f.isHidden() && !f.isDirectory())
				fileList.add(f);
		}
	}

	/**
	 * Dresse la liste des fichiers present dans le repertoir passe en parametre
	 * et des sous repertoires de maniere recurcive.
	 * 
	 * @param path
	 *            le repertoir a parcourir
	 * @param fileList
	 *            la liste des fichier a construire
	 * @param filtre
	 *            chaine devant etre presente dans le nom de fichier pour que
	 *            celui ci soit ajoute a la liste, null desactive le filtre
	 * @param ignoreHidden
	 *            demande d'ignorer les fichiers et dossier caché
	 */
	public final void getFileTree(File path, List<File> fileList, String filtre, boolean ignoreHidden) {
		if (!path.exists() || !path.canRead())
			return;
		if (path.isHidden() && ignoreHidden)
			return;
		if (path.isDirectory()) {
			File sub[] = path.listFiles();
			for (File f : sub)
				getFileTree(f, fileList, filtre, ignoreHidden);
		} else {
			if (filtre == null)
				fileList.add(path);
			else if (path.getName().contains(filtre))
				fileList.add(path);
		}
	}

	/**
	 * supprime un fichier ou un repertoire complet de maniere recurcive
	 * 
	 * @param path
	 *            le chemin du fichier (ou du repertoir) a supprimer
	 * @return true si tout s'est bien passe, false sinon
	 */
	public final boolean deleteFile(String path) {
		boolean result = false;
		File file = new File(path);
		if (file.exists()) {
			if (file.isDirectory())
				result = deleteDirectory(file, false);
			else
				result = file.delete();
		}
		return result;
	}

	/**
	 * supprime le contenu d'un repertoire
	 * 
	 * @param path
	 *            le chemin du repertoir vider
	 * @return true si tout s'est bien passe, false sinon
	 */
	public final boolean cleanDir(String path) {
		boolean result = false;
		File file = new File(path);
		if (file.exists()) {
			if (file.isDirectory())
				result = deleteDirectory(file, true);
		}
		return result;
	}

	/*
	 * methodes de supression reel des repertoires (recurcive) en option, on peu
	 * garder le repertoire racine si keepDir = true
	 */
	private final boolean deleteDirectory(File path, boolean keepDir) {
		boolean resultat = true;

		if (path.exists()) {
			File[] files = path.listFiles();
			for (int i = 0; i < files.length; i++) {
				if (files[i].isDirectory()) {
					resultat &= deleteDirectory(files[i], false);
				} else {
					resultat &= files[i].delete();
				}
			}
		}
		if (!keepDir) // si on ne garde pas la racine ...
			resultat &= path.delete();
		return resultat;
	}

	/**
	 * methode permetan de substituer toutes les instances d'une chaine dans un
	 * fichier. si les paramettres backupFile est null on travail avec un
	 * fichier temporaire.<br/>
	 * 
	 * @param FileToChange
	 *            fichier d'entree
	 * @param stringToFind
	 *            chaine a reperer
	 * @param substitutionString
	 *            chaine utilise pour le remplacement
	 * @param backupFile
	 *            fichier de sauvegarde
	 * @return true si la substitution s'est bien deroulée false sinon.
	 */
	public final boolean substitute(String FileToChange, String stringToFind, String substitutionString,
			String backupFile) {
		String line;
		File ofile = null;
		boolean del_tmp = false;
		try {
			if (backupFile == null) {
				ofile = File.createTempFile(FileToChange, null);
				del_tmp = true;
			} else
				ofile = new File(backupFile);

			// si la sortie existe on arrete
			if (ofile.exists())
				if (!ofile.delete())
					return false;

			File ifile = new File(FileToChange);
			if (!ifile.renameTo(ofile)) {
				this.logError(" can't rename original file");
				return false;
			}
			ifile = ofile;
			ofile = new File(FileToChange);
			ofile.createNewFile();
			ofile.setWritable(true);
			BufferedReader bufferedreader = new BufferedReader(new FileReader(ifile));
			BufferedWriter bufferedwriter = new BufferedWriter(new FileWriter(ofile, false));
			while ((line = bufferedreader.readLine()) != null) {
				if (line.contains(stringToFind))
					line = line.replace(stringToFind, substitutionString);
				bufferedwriter.write(line);
				bufferedwriter.newLine();
			}
			bufferedreader.close();
			bufferedwriter.close();
			if (del_tmp)
				ifile.delete();
		} catch (IOException e) {
			this.log("Replacement failed: " + e.getLocalizedMessage());
			return false;

		}
		this.log("Replacement sucesfully done.");
		return true;
	}

	/**
	 * Renvoi la date courrante selon le format specifie:
	 * 
	 * @param format
	 *            format de la date - "en" 1999/06/16 16h35 - "fr" 20/03/2001
	 *            17h10 - SimpleDateFormat parametre
	 * @return la chaine representant la date
	 */
	public synchronized final String date2String(String format) {
		if (format.equals("fr"))
			return new SimpleDateFormat("dd/MM/yyyy hh'h'mm").format(new Date());
		if (format.equals("en"))
			return new SimpleDateFormat("yyyy/MM/dd hh'h'mm a").format(new Date());
		return new SimpleDateFormat(format).format(new Date());
	}

}
