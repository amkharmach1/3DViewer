package modele;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
/**
 * 
 * @author planckea
 *
 */
public class PlyReader {

	private ObjectErrorControl oec;


	private static Scanner sc;
	/**
	 * Les patterns que nous retrouverons dans un fichier ply
	 */
	private static final String FLOAT = "-?[0-9]+((\\.[0-9]+)?(e(\\+|-)?[0-9]+)?)" ;
	private static final String PATTERN_POINT = "^(\\s)*" + FLOAT + "\\s+" + FLOAT + "\\s+" + FLOAT + "(\\s)*$" ;
	private static final String PATTERN_FACE = "^\\s*( ?[0-9]+ ?)*\\s*(([0-9]+\\s*){3})?(\\s)*$";
	private static final Pattern POINT = Pattern.compile("[0-9]+");
	private static final Pattern X_POINT = Pattern.compile("^-?(\\s)*" + FLOAT + "\\s+");
	private static final Pattern Y_POINT = Pattern.compile("\\s+" + FLOAT + "\\s+");
	private static final Pattern Z_POINT = Pattern.compile("\\s+" + FLOAT + "\\s*$");
	private static final Pattern NOMBRE_POINT_DANS_FACE = Pattern.compile("^?[0-9]+");


	/**
	 * Les differents compteurs
	 */

	private int nbPoint;
	private int nbFace;
	private String author;
	private String description;

	//constructor
	public PlyReader() {

	}
	/**
	 * Lis le header du fichier et verifie qu'il est valide
	 * @return Vrai si le chemin du fichier est valide, que c'est un ply et que la créations des points et faces est valide, faux sinon.
	 * @throws FileNotFoundException Quand le chemin du fichier est incorrect
	 */
	public boolean initPly(String pathToPly) throws FileNotFoundException {
		oec = new ObjectErrorControl();
		final String END_HEADER_STRING = "end_header";
		final String VERTEX_STRING = "element vertex ";
		final String FACE_STRING = "element face ";
		final String AUTHOR_STRING = "comment made by ";
		final String DESCRIPTION_STRING = "comment ";

		sc = new Scanner(new File(pathToPly));
		boolean endHeader = false;
		String tmpReader = "";
		tmpReader = sc.nextLine();
		oec.incrCptLineDeUn();
		if(!tmpReader.contains("ply")) return false;
		while(sc.hasNextLine() && !endHeader) {
			tmpReader = sc.nextLine();
			oec.incrCptLineDeUn();
			if(tmpReader.contains(VERTEX_STRING)) {
				nbPoint = Integer.parseInt(tmpReader.substring(VERTEX_STRING.length(), tmpReader.length()));
			}
			if(tmpReader.contains(FACE_STRING)) {
				nbFace = Integer.parseInt(tmpReader.substring(FACE_STRING.length(), tmpReader.length()));
			}
			if(tmpReader.contains(AUTHOR_STRING))
				author = tmpReader.substring(AUTHOR_STRING.length(), tmpReader.length());
			if(tmpReader.contains(DESCRIPTION_STRING) && !tmpReader.contains(AUTHOR_STRING))
				description = tmpReader.substring(DESCRIPTION_STRING.length(), tmpReader.length());

			if(tmpReader.equals(END_HEADER_STRING)) 
				endHeader = true;

		}
		if(nbPoint==0 || nbFace==0)
			return false;
		return true;
	}

	/**
	 * Lis les lignes correspondantes aux points et aux faces dans le ply et les créer
	 * @return true si tout s'est bien passé
	 * @throws CreationPointException Quand il y a une erreur de format dans un point
	 * @throws CreationFaceException Quand il y a une erreur de format dans une face
	 */
	public Model3D getPly(String pathToPly) {
		Point.resetNAuto();
		Model3D aPlyFile = new Model3D(nbPoint);
		String tmpReader = "";
		while(sc.hasNextLine()) {
			oec.incrCptLineDeUn();
			tmpReader = sc.nextLine();
			try {
				analyseString(tmpReader,aPlyFile);	
			}catch(CreationFormatPointException cfpe){
				System.out.println(cfpe.getMessage());
				aPlyFile.getErrorList().getListPointErreur().add(cfpe.getMessage());
				continue;
			}catch(CreationPointManquantException cpme) {
				System.out.println(cpme.getMessage());
				aPlyFile.getErrorList().getListPointErreur().add(cpme.getMessage());
				continue;
			}catch(CreationFormatFaceException cffe) {
				System.out.println(cffe.getMessage());
				aPlyFile.getErrorList().getListFaceErreur().add(cffe.getMessage());
				continue;
			}catch(CreationNombreFaceException cnfe) {
				System.out.println(cnfe.getMessage());
				aPlyFile.getErrorList().getListFaceErreur().add(cnfe.getMessage());
				continue;
			}
		}
		sc.close();
		double rapport = 0;
		boolean rapportHorizontal = false;
		if(oec.getMaxX() - oec.getMinX() > oec.getMaxY() - oec.getMinY()) {
			rapport = oec.getMaxX() - oec.getMinX();
			rapportHorizontal = true;
		}else
			rapport = oec.getMaxY() - oec.getMinY();
		aPlyFile.setRapportHorizontal(rapportHorizontal);
		aPlyFile.setRapport(rapport);
		aPlyFile.setPointDuMilieu(new Point((oec.getMinX() + oec.getMaxX())/2, (oec.getMinY() + oec.getMaxY())/2, (oec.getMinZ()+ oec.getMaxZ()) / 2));
		aPlyFile.initMatrice();
		if(author != null)
			aPlyFile.setAuthor(author);
		if(description != null)
			aPlyFile.setDescription(description);
		return aPlyFile;
	}
	/**
	 * Analyse la String correspondant à la ligne lue et dispatche le traitement selon la ligne dans d'autres methodes
	 * @param tmpReader la ligne lue
	 * @param aPlyFile l'objet sur lequel on ajouteras les points et face crées
	 * @throws CreationFormatFaceException Si une face ne corresponds pas à une notation normale
	 * @throws CreationFormatPointException Si un point ne corresponds pas à une notation normale
	 * @throws CreationPointManquantException Si il manque des points quand on arrive à la lecture des faces
	 * @throws CreationNombreFaceException  Si il manque des faces quand on arrive à la fin du fichier
	 */
	private void analyseString(String tmpReader,Model3D aPlyFile) throws CreationFormatFaceException, CreationFormatPointException, CreationPointManquantException, CreationNombreFaceException {
		if(oec.getCptPoint() == nbPoint) 
			oec.setDansPoint(false);
		if(oec.isDansPoint() && Pattern.matches(PATTERN_POINT, tmpReader)) {
			creationPoint(tmpReader,aPlyFile.getTabPoint());			
			oec.incrCptPointDeUn();
		}else if(oec.isDansPoint() && !Pattern.matches(PATTERN_POINT, tmpReader) && !Pattern.matches(PATTERN_FACE,tmpReader)) {
			creationPoint("0 0 0 ",aPlyFile.getTabPoint());
			oec.incrCptPointDeUn();
			throw new CreationFormatPointException("Le format du point " + tmpReader + " n'est pas conforme ligne : " + oec.getCptLine());
		}else if(oec.isDansPoint() && 	oec.getCptPoint()  < nbPoint && Pattern.matches(PATTERN_FACE,tmpReader) ) {
			while(oec.getCptPoint() < nbPoint) {
				creationPoint("0 0 0 ",aPlyFile.getTabPoint());
				oec.incrCptPointDeUn();
				oec.incrCptPointManquantDeUn();
			}
			creationFace(tmpReader,aPlyFile.getArrayListFace(), aPlyFile.getTabPoint());
			oec.incrCptFaceDeUn();
			throw new CreationPointManquantException("Il manquait " + 	oec.getCptPointManquant() + " point(s) dans votre fichier ply");
		}

		if(!oec.isDansPoint() && Pattern.matches(PATTERN_FACE,tmpReader)) {
			creationFace(tmpReader,aPlyFile.getArrayListFace(), aPlyFile.getTabPoint());
			oec.incrCptFaceDeUn();
		}
		if(!sc.hasNextLine() && oec.getCptFace() < nbFace){
			while(	oec.getCptFace() < nbFace) {
				creationFace("3 0 0 0 ",aPlyFile.getArrayListFace(), aPlyFile.getTabPoint());
				oec.incrCptFaceDeUn();
				oec.incrCptFaceManquanteDeUn();
			}
			throw new CreationNombreFaceException("Il manquait " + 	oec.getCptFaceManquante() + " face(s) dans votre fichier ply");
		}
	}

	/**
	 * Créé un point à partir d'un string et l'ajoutes à la hashmap du PlyFile
	 * @param tmpReader Le String correspondant à un point
	 * @param hashMapPoint la hashmap dans laquelle on ajouteras le point
	 * @return Vrai si on trouve bien les x points (1er nombre du string) faux sinon
	 */
	public boolean creationPoint(String tmpReader, Point[] tabPoint) {
		Matcher mx = X_POINT.matcher(tmpReader);
		Matcher my = Y_POINT.matcher(tmpReader);
		Matcher mz = Z_POINT.matcher(tmpReader);
		if(mx.find() && my.find() && mz.find()) {
			Point tmp = new Point(Double.parseDouble(mx.group()), Double.parseDouble(my.group()), Double.parseDouble(mz.group()));
			//System.out.println(tmp);
			if(oec.getMinX() == 0 && oec.getMaxX() == 0 && oec.getMinY() == 0 && oec.getMaxY() == 0 && oec.getMinZ() == 0 && oec.getMaxZ() == 0){
				oec.setMinX(tmp.getX());
				oec.setMaxX(tmp.getX());
				oec.setMinY(tmp.getY());
				oec.setMaxY(tmp.getY());
				oec.setMinZ(tmp.getZ());
				oec.setMaxX(tmp.getZ());
			}else {
				if(tmp.getX() < oec.getMinX())
					oec.setMinX(tmp.getX());
				if(tmp.getX() > oec.getMaxX())
					oec.setMaxX(tmp.getX());
				if(tmp.getY() < oec.getMinY())
					oec.setMinY(tmp.getY());
				if(tmp.getY() > oec.getMaxY())
					oec.setMaxY(tmp.getY());
				if(tmp.getZ() < oec.getMinZ())
					oec.setMinZ(tmp.getZ());
				if(tmp.getZ() > oec.getMaxZ())
					oec.setMaxZ(tmp.getZ());
			}
			tabPoint[tmp.getId()] = tmp;
			return true;
		}
		return false;
	}
	/**
	 * Creer une face avec les différents point qui la compose
	 * @param tmpReader le string contenant les points de la face
	 * @return 
	 * @throws CreationFormatFaceException 
	 */
	public boolean creationFace(String tmpReader,ArrayList<Face> listFace, Point[] tabPoint) throws CreationFormatFaceException {
		Matcher pointMatch = POINT.matcher(tmpReader);
		Matcher pointDansFace = NOMBRE_POINT_DANS_FACE.matcher(tmpReader);
		final int NOMBRE_COULEUR = 3;
		int cpt = 0;
		int nbPoint = 0;
		Face tmp = new Face();
		//Si on ne trouve pas une composante de la face, on creer une face triangulaire avec comme point le premier point de notre liste de point.
		if(!pointMatch.find() || !pointDansFace.find()) {
			while(cpt != 3) {
				tmp.addPoint(tabPoint[0]);
				cpt++;
			}
			listFace.add(tmp);
			throw new CreationFormatFaceException("Problème dans le format de la face " + tmpReader + " ligne : " + oec.getCptLine());
		}
		nbPoint = Integer.parseInt(pointDansFace.group());
		while(pointMatch.find() && cpt != nbPoint) {
			tmp.addPoint(tabPoint[Integer.parseInt(pointMatch.group())]);
			cpt++;
		}

		//Si il manque des points
		if(cpt != Integer.parseInt(pointDansFace.group())) {
			while(cpt != Integer.parseInt(pointDansFace.group())){
				tmp.addPoint(tabPoint[0]);
				cpt++;
			}
			listFace.add(tmp);
			throw new CreationFormatFaceException("Problème dans le format de la face " + tmpReader + " ligne : " + oec.getCptLine());
		}else {
			cpt = 0;
			while(pointMatch.find() && cpt < NOMBRE_COULEUR) {
				tmp.getRgbColor()[cpt] = Integer.parseInt(pointMatch.group());
				cpt++;
			}
		}
		listFace.add(tmp);
		return true;
	}
}
