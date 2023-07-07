/*
 * $Id: ASLMap.java 8530 2012-12-26 04:37:04Z uckelman $
 *
 * Copyright (c) 2013 by Brent Easton
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */

package VASL.build.module;

import VASL.LOS.LOSDataEditor;
import VASL.LOS.Map.Hex;
import VASL.LOS.Map.Location;
import VASL.LOS.Map.Terrain;
import VASL.LOS.counters.CounterMetadataFile;
import VASL.build.module.map.boardArchive.SharedBoardMetadata;
import VASL.build.module.map.boardPicker.ASLBoard;
import VASL.build.module.map.boardPicker.BoardException;
import VASL.build.module.map.boardPicker.Overlay;
import VASL.build.module.map.boardPicker.VASLBoard;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.configure.ColorConfigurer;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.tools.DataArchive;
import VASSAL.tools.ErrorDialog;
import VASSAL.tools.imageop.Op;
import org.jdom2.JDOMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;

import static VASSAL.build.GameModule.getGameModule;
import static java.lang.Math.cos;

public class ASLMap extends Map {

    // FredKors popup menu
    private JPopupMenu m_mnuMainPopup = null;

    // board metadata
    //TODO: the shared metadata is static so it doesn't have to be passed around. Is there a better way?
    private VASL.LOS.Map.Map VASLMap;
    private static final String sharedBoardMetadataFileName = "boardData/SharedBoardMetadata.xml"; // name of the shared board metadata file
    private static SharedBoardMetadata sharedBoardMetadata = null;
    private boolean legacyMode;                     // true if unable to create a VASL map or LOS data is missing
    // counter metadata
    private static CounterMetadataFile counterMetadata = null;

    // used to log errors in the VASSAL error log
    private static final Logger logger = LoggerFactory.getLogger(ASLMap.class);
    private ShowMapLevel m_showMapLevel = ShowMapLevel.ShowAll;
    // background color preference
    private static final String preferenceTabName = "VASL";

  public ASLMap() {


      super();

      try {
          readMetadata();
      } catch (JDOMException e) {

          // give up if there's any problem reading the shared metadata file
          ErrorDialog.bug(e);
      }
      m_mnuMainPopup = new JPopupMenu();

      // FredKors: creation of the toolbar button
      // that opens the popup menu
      JButton l_Menu = new JButton();

    try
    {
        l_Menu.setIcon(new ImageIcon(Op.load("QC/menu.png").getImage(null)));
    }
    catch (Exception ex) 
    {
        ex.printStackTrace();
    }
    
    l_Menu.setMargin(new Insets(0, 0, 0, 0));
    l_Menu.setAlignmentY(0.0F);
    
    l_Menu.addActionListener(new ActionListener() 
    {
        public void actionPerformed(ActionEvent evt) 
        {
            if (evt.getSource() instanceof JButton)
                m_mnuMainPopup.show((JButton)evt.getSource(), 0, 0);
        }
    });

    // add the first element to the popupp menu
    JMenuItem l_SelectItem = new JMenuItem("Select");
    l_SelectItem.setBackground(new Color(255,255,255));
    m_mnuMainPopup.add(l_SelectItem);
    m_mnuMainPopup.addSeparator();    

    // add the menu button to the toolbar
    getToolBar().add(l_Menu); 
    getToolBar().addSeparator();
    // background color preference
    final ColorConfigurer backgroundcolor = new ColorConfigurer("backcolor", "Set Color of space around Map (requires VASL restart)", Color.white);
    getGameModule().getPrefs().addOption(preferenceTabName, backgroundcolor);


}
  
  /*
   *  Work-around for VASL board being 1 pixel too large causing double stacks to form along board edges.
   *  Any snap to a board top or left edge half hex, bump it 1 pixel up or left on to the next board.
   *  
   *  */
  public Point snapTo(Point p) 
  { // FredKors 22/12/2013
    final Point l_pSnapTo = super.snapTo(p);
    Point l_pShiftedXY, l_pShiftedY, l_pShiftedX;
    
    l_pShiftedXY = new Point (l_pSnapTo);

    l_pShiftedXY.x -= 3;
    l_pShiftedXY.y -= 3; // I move the snap point 3 pixel up and left: if the map changes, the snapTo could return a different point, otherwise nothing changes
    l_pShiftedXY = super.snapTo(l_pShiftedXY);
    
    if (findBoard(l_pShiftedXY) != null) //  Return to the snapTo point if I moved off the top border or the left border
        return l_pShiftedXY;
    
    l_pShiftedY = new Point (l_pSnapTo);
    
    l_pShiftedY.y -= 3; // I move the snap point 3 pixel up: if the map changes, the snapTo could return a different point, otherwise nothing changes
    l_pShiftedY = super.snapTo(l_pShiftedY);
    
    if (findBoard(l_pShiftedY) == null) // I moved off the top border, return to the snapTo point
        l_pShiftedY.y = l_pSnapTo.y;
    
    l_pShiftedX = new Point (l_pShiftedY);
    
    l_pShiftedX.x -= 3;// I move the snap point 3 pixel left: if the map changes, the snapTo could return a different point, otherwise nothing changes
    l_pShiftedX = super.snapTo(l_pShiftedX);
    
    if (findBoard(l_pShiftedX) == null) // I moved off the left border
        return l_pShiftedY;
    
    return l_pShiftedX;        
    
  }
  // return the popup menu
  public JPopupMenu getPopupMenu()
  {
      return m_mnuMainPopup;
  }

    @Override
    public synchronized void setBoards(Collection<Board> c) {

        for (Board boardc: c) {
            VASLBoard testboardexists = (VASLBoard) boardc;
            if (testboardexists.getVASLBoardArchive() == null) {
                GameModule.getGameModule().getChatter().send("Board missing. Auto-synching of boards requires board directory in board picker matches the board directory set in preferences. Close this game and start new game");
                return;
            }
        }
        super.setBoards(c);
        String info = "Using board(s): ";
        for (Board board : boards) {
            ASLBoard b = (ASLBoard) board;
            info += b.getName() + "(v" + b.getVersion() + ") ";
        }
        GameModule.getGameModule().warn(info);
        buildVASLMap();
        // Add OBObserver location
        if (VASLMap!=null){
            for (GameComponent gc: GameModule.getGameModule().getGameState().getGameComponents()){
                String classname = gc.getClass().getName();
                if (gc.getClass().getName() =="VASL.build.module.OBA" ){
                    OBA oba = (OBA) gc;
                    oba.checkforOBO();
                }
            }
        }

    }

    /**
     * read the shared board metadata
     */
    private void readMetadata() throws JDOMException {

        DataArchive archive = GameModule.getGameModule().getDataArchive();
        // shared board metadata
        try (InputStream inputStream =  archive.getInputStream(sharedBoardMetadataFileName)) {
            sharedBoardMetadata = new SharedBoardMetadata();
            sharedBoardMetadata.parseSharedBoardMetadataFile(inputStream);

        // give up on any errors
        } catch (IOException e) {
            sharedBoardMetadata = null;
            throw new JDOMException("Cannot read the shared metadata file", e);
        } catch (JDOMException e) {
            sharedBoardMetadata = null;
            throw new JDOMException("Cannot read the shared metadata file", e);
        } catch (NullPointerException e) {
            sharedBoardMetadata = null;
            throw new JDOMException("Cannot read the shared metadata file", e);
        }
    }

    /**
     * Builds the VASL map
     */
    protected void buildVASLMap() {
        // set background color from preference
        super.bgColor = (Color) getGameModule().getPrefs().getValue("backcolor");
        Boolean alwaysontop = Boolean.TRUE.equals(getGameModule().getPrefs().getValue("PWAlwaysOnTop"));
        getGameModule().getPlayerWindow().setAlwaysOnTop(alwaysontop);
        repaint();
        legacyMode = false;
        boolean nullBoards = false; // are null boards being used?

        // create an empty VASL map of the correct size
        LinkedList<VASLBoard> VASLBoards = new LinkedList<VASLBoard>(); // keep the boards so they are instantiated once
        try {

            // see if there are any legacy boards in the board set
            // and determine the size of the map
            final Rectangle mapBoundary = new Rectangle(0,0);
            double hexHeight = 0.0;
            double hexWidth = 0.0;
            for(Board b: boards) {

                final VASLBoard board = (VASLBoard) b;

                // ignore null boards
                if(!"NUL".equals(b.getName()) && !"NULV".equals(b.getName())){

                    if(board.isLegacyBoard()) {
                        throw new BoardException("VASL LOS disabled - Board " + board.getName() + " does not support LOS checking. VASSAL los active");
                    }

                    mapBoundary.add(b.bounds());
                    VASLBoards.add(board);

                    // make sure the hex geometry of all boards is the same
                    if (hexHeight != 0.0 && Math.round(board.getHexHeight()) != Math.round(hexHeight) || hexWidth != 0.0 && Math.round(board.getHexWidth()) != Math.round(hexWidth)) {
                        throw new BoardException("VASL LOS disabled: Map configuration contains multiple hex sizes. VASSAL los active");
                    }
                    hexHeight = board.getHexHeight();
                    hexWidth = board.getHexWidth();
                }
                else {
                    nullBoards = true;
                }
            }
            // this is a hack to fix problem with board geometry. Standard geo hexes cannot have a width greater than 56.25 or they will exceed the board size of 1800 pixels
            // even if they are 56.3125 in size
            // need to edit BoardMetaData.xml to change hexHeight to 56.25
            if (hexWidth == 56.3125){hexWidth = 56.25;}


            // remove the edge buffer from the map boundary size
            mapBoundary.width -= edgeBuffer.width;
            mapBoundary.height -= edgeBuffer.height;

            // create the VASL map
            //DR added new variables to pass cropping values
            String passgridconfig="Normal";
            boolean isCropping=false;
            int fullhexadj=0;

            VASLBoard b = VASLBoards.get(0); // we can use the geometry of any board - assuming all are the same
            if (b.getVASLBoardArchive().getHexGridConfig() != null) {passgridconfig = b.getVASLBoardArchive().getHexGridConfig();}
            if (b.isCropped()) { isCropping=true;}
            if (b.nearestFullRow) {
                passgridconfig = "FullHex";
                fullhexadj=-1;
                if (b.getCropBounds().getX() == 0) {passgridconfig = "FullHexLeftHalf"; fullhexadj=0;}
                if (b.getCropBounds().getMaxX() == b.getUncroppedSize().getWidth()) {passgridconfig = "FullHexRightHalf"; fullhexadj=0;}
            }
            //if (!(b.getA1CenterX()==0) && !(b.getA1CenterX()==-999)) { passgridconfig= passgridconfig +"Offset";}
            double passA1CenterY = b.getA1CenterY();
            if (!(b.getA1CenterX()==0) && !(b.getA1CenterX()==-999) && !(b.getA1CenterX()==-901)) {
                if (b.getCropBounds().getX() != 0) {
                    passgridconfig = passgridconfig + "Offset";  // only need to set this if cropping the left edge when board has offset (ie RB and RO)
                }
                //if(b.getA1CenterY()==65 && b.getCropBounds().getX()==0){passA1CenterY =32.25;}
            }
            VASLMap = new VASL.LOS.Map.Map(
                    hexWidth,
                    hexHeight,
                    (int) Math.round(mapBoundary.width/ b.getHexWidth()) + 1 + fullhexadj,
                    (int) Math.round(mapBoundary.height/ b.getHexHeight()),
                    b.getA1CenterX(),
                    passA1CenterY,
                    mapBoundary.width,
                    mapBoundary.height,
                    sharedBoardMetadata.getTerrainTypes(), passgridconfig, isCropping);
        }

        // clean up and fall back to legacy mode if an unexpected exception is thrown
        catch (BoardException e) {

            setLegacyMode();
            logError(e.toString());
            GameModule.getGameModule().getChatter().send(e.toString());
        }
        catch (Exception e) {

            setLegacyMode();
            VASLBoards = null;
            logError("LOS disabled - unexpected error");
            logException(e);
            GameModule.getGameModule().getChatter().send("LOS disabled - unexpected error: " + e.toString());
        }

        // add the boards to the VASL map
        try {

            // load the LOS data
            if(!legacyMode) {

                for (VASLBoard board : VASLBoards) {

                    // read the LOS data and flip/crop the board if needed
                    // DR added variable to support cropping and flipping
                    String croptype="Normal"; boolean isCropping=false; double Fullhexadj= 0;
                    double gridadj=0;

                    if (board.nearestFullRow) {
                        croptype="FullHex";
                        Fullhexadj= board.getHexWidth()/2;
                        if (board.getCropBounds().getX()==0) {croptype="FullHexLeftHalf";}
                        if (board.getCropBounds().getMaxX()== board.getUncroppedSize().getWidth()) {croptype="FullHexRightHalf";}
                    }
                    if(board.isCropped()) {
                        isCropping=true;
                        if (!croptype.contains("LeftHalf")) {
                            if (!(board.getA1CenterX() == -901)) {
                                gridadj = board.getA1CenterX() - Fullhexadj;
                                if (board.getCropBounds().width== -1) {gridadj=0;}
                            } else {
                                gridadj = -Fullhexadj;
                            }
                        }

                    }
                    if (!(board.getA1CenterX()==0) && !(board.getA1CenterX()==-999) && !(board.getA1CenterX()==-901)) {
                        if (board.getCropBounds().getX() != 0) {
                            croptype = croptype + "Offset";  // only need to set this if cropping the left edge when board has offset (ie RB and RO)
                        }
                    }
                    VASL.LOS.Map.Map LOSData = board.getLOSData(sharedBoardMetadata.getTerrainTypes(), croptype, isCropping, gridadj);

                    // apply the SSR changes, crop and flip if needed
                    board.applyColorSSRules(LOSData, sharedBoardMetadata.getLOSSSRules(), gridadj);

                    if(board.isCropped()) {
                        LOSData = board.cropLOSData(LOSData);
                    }

                    if(board.isReversed()){
                        LOSData.flip();
                    }
                    //new code for adding overlays to LOS
                    LOSData = adjustLOSForOverlays(board, LOSData);
                    // add the board LOS data to the map
                    // .insertMap is designed to work with only geo board thus need to test for non-geo boards (in this situation geo boards inclues AP boards and deluxe boards)
                    if ((board.getWidth()==33 && board.getHeight()==10) || (board.getWidth()==17 && board.getHeight()==20) || (board.getWidth() == 15 && board.getHeight() ==5)) {
                        //line below is not a good fix; make sure it works in all situations or change
                            int cropadj=1;  // ensures that cropping a board by row works properly DR (rows such as A7 have uneven total height which results in incorrect choice from gridToHex)
                            if (!VASLMap.insertMap(
                                    LOSData,
                                    VASLMap.gridToHex(board.getBoardLocation().x, board.getBoardLocation().y + cropadj + (nullBoards ? 1 : 0)))) {

                                // didn't work, so assume an unsupported feature
                                throw new BoardException("VASL LOS Disabled: Unable to insert board " + board.getName() + " into the VASL map. VASSAL los active");
                            }
                    }
                    else {
                            // add board LOS data for non-standard size board
                        //line below is not a good fix; make sure it works in all situations or change
                        int cropadj=1;  // ensures that cropping a board by row number works properly DR (rows such as A7 have uneven total height which results in incorrect choice from gridToHex)
                        if (VASLBoards.size()==1){
                            VASLMap.insertOneMap(LOSData);
                        } else {
                            if (!VASLMap.insertNonGeoMap(
                                    LOSData,
                                    VASLMap.gridToHex(board.getBoardLocation().x, board.getBoardLocation().y + cropadj + (nullBoards ? 1 : 0)))) {

                                // didn't work, so assume an unsupported feature
                                throw new BoardException("VASL LOS Disabled: Unable to insert board " + board.getName() + " into the VASL map. VASSAL los active");
                            }
                        }
                    }
                }
                GameModule.getGameModule().warn("VASL LOS Enabled");
            }
        }
        catch (BoardException e) {

            setLegacyMode();
            logError(e.toString());
            GameModule.getGameModule().getChatter().send("VASL LOS Disabled: " + e.toString() + ". VASSAL los active");
        }
        catch (Exception e) {

            setLegacyMode();
            logError("LOS disabled - unexpected error");
            logException(e);
            GameModule.getGameModule().getChatter().send("VASL LOS disabled: " + e.toString() + ". VASSAL los active");
        }
        finally {
            // free up memory
            VASLBoards = null;
        }
    }

    private VASL.LOS.Map.Map adjustLOSForOverlays(VASLBoard board, VASL.LOS.Map.Map losdata){
        VASL.LOS.Map.Map newlosdata = losdata;
        final Enumeration overlays = board.getOverlays();
        while (overlays.hasMoreElements()) {
            Overlay o = (Overlay) overlays.nextElement();
            if(o.getName().equals("")){continue;} // prevents error when using underlays (which are added as overlays)
            if(o.getName().contains("BSO") && (!o.getName().contains("BSO_LFT3"))){continue;} // prevents error when using BSO which are handled elsewhere
            // BSO_LFT3 may be a special case; treat it as so for now; if find others then need to develop a proper solution
            Rectangle ovrRec = o.bounds();
            // get the image as a buffered image
            Image i = o.getImage();
            BufferedImage bi = new BufferedImage(i.getWidth(null), i.getHeight(null), BufferedImage.TYPE_INT_ARGB);
            Graphics2D bGr = bi.createGraphics();
            bGr.drawImage(i, 0, 0, null);
            bGr.dispose();
            if (o.getFile().getName().equalsIgnoreCase("ovrH")) {
                setHillockTerrain(board, newlosdata, bi, ovrRec);
            } else if (o.getFile().getName().equalsIgnoreCase("ovrD")) {
                setDierTerrain(board, newlosdata, bi, ovrRec);
                setDierLip(newlosdata);
            } else if (o.getFile().getName().equalsIgnoreCase("ovrSD")) {
                setSandDuneTerrain(board, newlosdata, bi, ovrRec);
            } else if (o.getFile().getName().equalsIgnoreCase("ovrW")) {
                setWadiTerrain(board, newlosdata, bi, ovrRec);
            } else {
                String terraintype = getoverlayterraintype(o);
                setOverlayTerrain(board, newlosdata, bi, ovrRec, terraintype, o.getpreserveelevation());
            }

        }

       return newlosdata;

    }

    private void setHillockTerrain(VASLBoard board, VASL.LOS.Map.Map newlosdata, BufferedImage bi, Rectangle ovrRec){
        if (board.isReversed()){
            // flip the overlay grid
            for (int x = 0; x < bi.getWidth(); x++) {
                for (int y = 0; y < bi.getHeight(); y++) {
                    if (newlosdata.onMap(newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1)) {
                        int c = bi.getRGB(x, y);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Hillock";
                            Terrain terr;
                            //Retrieving the R G B values
                            Color color = getRGBColor(c);
                            Color testcolor = new Color(114, 83, 42); //have to use method as several colors have same RGB values
                            if (color.equals(testcolor)) {
                                terraintouse = "Hillock Summit";
                            }
                            newlosdata.setGridTerrainCode(newlosdata.getTerrain(terraintouse).getType(), newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1);
                            if (!(newlosdata.gridToHex(newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1).getCenterLocation().getTerrain().getName().equals("Hillock Summit"))) {
                                newlosdata.gridToHex(newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1).getCenterLocation().setTerrain(newlosdata.getTerrain(terraintouse));
                            } else {
                                newlosdata.gridToHex(newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1).getCenterLocation().setBaseHeight(1);
                            }
                        }
                    }
                }
            }
        } else {
            for (int x = 0; x < bi.getWidth(); x++) {
                for (int y = 0; y < bi.getHeight(); y++) {
                    if (newlosdata.onMap(x + ovrRec.x, y + ovrRec.y)) {
                        int c = bi.getRGB(x, y);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Hillock";
                            Terrain terr;
                            //Retrieving the R G B values
                            Color color = getRGBColor(c);
                            Color testcolor = new Color(114, 83, 42); //have to use method as several colors have same RGB values
                            if (color.equals(testcolor)) {
                                terraintouse = "Hillock Summit";
                            }
                            newlosdata.setGridTerrainCode(newlosdata.getTerrain(terraintouse).getType(), x + ovrRec.x, y + ovrRec.y);
                            if (!(newlosdata.gridToHex(x + ovrRec.x, y + ovrRec.y).getCenterLocation().getTerrain().getName().equals("Hillock Summit"))) {
                                newlosdata.gridToHex(x + ovrRec.x, y + ovrRec.y).getCenterLocation().setTerrain(newlosdata.getTerrain(terraintouse));
                            } else {
                                newlosdata.gridToHex(x + ovrRec.x, y + ovrRec.y).getCenterLocation().setBaseHeight(1);
                            }
                        }
                    }
                }
            }
        }

    }
    private void setDierTerrain(VASLBoard board, VASL.LOS.Map.Map newlosdata, BufferedImage bi, Rectangle ovrRec){
        if (board.isReversed()){
            // flip the overlay grid
            for (int x = 0; x < bi.getWidth(); x++) {
                for (int y = 0; y < bi.getHeight(); y++) {
                    if (newlosdata.onMap(newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1)) {
                        int c = bi.getRGB(x, y);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Dier";
                            Terrain terr;
                            //Retrieving the R G B values
                            Color color = getRGBColor(c);
                            int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
                            if (terrint >= 0) {
                                terr = newlosdata.getTerrain(terrint);
                                if (terr.getName().contains("Scrub")) {
                                    terraintouse = "Scrub";
                                } else if (terr.getName().equals("Dier")) {
                                    terraintouse = "Dier";
                                }
                            }
                            newlosdata.setGridTerrainCode(newlosdata.getTerrain(terraintouse).getType(), newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1);
                            if (!(newlosdata.gridToHex(newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1).getCenterLocation().getTerrain().getName().equals("Scrub"))) {
                                newlosdata.gridToHex(newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1).getCenterLocation().setTerrain(newlosdata.getTerrain(terraintouse));
                            }
                        }
                    }
                }
            }
        } else {

            for (int x = 0; x < bi.getWidth(); x++) {
                for (int y = 0; y < bi.getHeight(); y++) {
                    if (newlosdata.onMap(x + ovrRec.x, y + ovrRec.y)) {
                        int c = bi.getRGB(x, y);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Dier";
                            Terrain terr;
                            //Retrieving the R G B values
                            Color color = getRGBColor(c);
                            int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
                            if (terrint >= 0) {
                                terr = newlosdata.getTerrain(terrint);
                                if (terr.getName().contains("Scrub")) {
                                    terraintouse = "Scrub";
                                } else if (terr.getName().equals("Dier")) {
                                    terraintouse = "Dier";
                                }
                            }
                            newlosdata.setGridTerrainCode(newlosdata.getTerrain(terraintouse).getType(), x + ovrRec.x, y + ovrRec.y);
                            if (!(newlosdata.gridToHex(x + ovrRec.x, y + ovrRec.y).getCenterLocation().getTerrain().getName().equals("Scrub"))) {
                                newlosdata.gridToHex(x + ovrRec.x, y + ovrRec.y).getCenterLocation().setTerrain(newlosdata.getTerrain(terraintouse));
                            }
                        }
                    }
                }
            }
        }

    }
    private void setDierLip(VASL.LOS.Map.Map newlosdata){
        // step through each hex and reset the terrain.
        if(newlosdata.getMapConfiguration().equals("TopLeftHalfHeightEqualRowCount") || newlosdata.getA1CenterY()==65){
            for (int x = 0; x < newlosdata.getWidth(); x++) {
                for (int y = 0; y < newlosdata.getHeight(); y++) { // no extra hex for boards where each col has same number of rows (eg RO)
                    if(newlosdata.getHex(x, y).getCenterLocation().getTerrain().getName().equals("Dier")){
                        for (int a =0; a <6; a++) {
                            Hex testhex = newlosdata.getAdjacentHex(newlosdata.getHex(x, y), a);
                            if ((testhex==null) || !(testhex.getCenterLocation().getTerrain().getName().equals("Dier"))) {
                                newlosdata.getHex(x, y).setHexsideTerrain(a, newlosdata.getTerrain("Dier Lip"));
                                newlosdata.getHex(x,y).setHexsideLocationTerrain(a, newlosdata.getTerrain("Dier Lip"));
                            }
                        }
                    }
                }
            }
        } else {
            for (int x = 0; x < newlosdata.getWidth(); x++) {
                for (int y = 0; y < newlosdata.getHeight() + (x % 2); y++) { // add 1 hex if odd
                    if(newlosdata.getHex(x, y).getCenterLocation().getTerrain().getName().equals("Dier")){
                        for (int a =0; a <6; a++) {
                            Hex testhex = newlosdata.getAdjacentHex(newlosdata.getHex(x, y), a);
                            if ((testhex==null) || !(testhex.getCenterLocation().getTerrain().getName().equals("Dier"))) {
                                newlosdata.getHex(x, y).setHexsideTerrain(a, newlosdata.getTerrain("Dier Lip"));
                                newlosdata.getHex(x,y).setHexsideLocationTerrain(a, newlosdata.getTerrain("Dier Lip"));
                            }
                        }
                    }
                }
            }
        }
    }

    private void setSandDuneTerrain(VASLBoard board, VASL.LOS.Map.Map newlosdata, BufferedImage bi, Rectangle ovrRec){
        if (board.isReversed()){
            // flip the overlay grid
            for (int x = 0; x < bi.getWidth(); x++) {
                for (int y = 0; y < bi.getHeight(); y++) {
                    if (newlosdata.onMap(newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1)) {
                        int c = bi.getRGB(x, y);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Sand Dune, Low";
                            Terrain terr;
                            //Retrieving the R G B values
                            Color color = getRGBColor(c);
                            int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
                            if (terrint >= 0) {
                                terr = newlosdata.getTerrain(terrint);
                                if (terr.getName().equals("Dune, Crest Low")) {
                                    terraintouse = "Dune, Crest Low";
                                } else if (terr.getName().contains("Scrub")) {
                                    terraintouse = "Scrub";
                                }
                            }
                            newlosdata.setGridTerrainCode(newlosdata.getTerrain(terraintouse).getType(), newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1);
                            if (terraintouse.equals("Dune, Crest Low")) {
                                setDuneCrest(newlosdata,newlosdata.getGridWidth() - -x -1, newlosdata.getGridHeight() -y -1, ovrRec, true);
                            }
                            newlosdata.gridToHex(newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1).getCenterLocation().setTerrain(newlosdata.getTerrain("Sand Dune, Low"));

                        }
                    }
                }
            }
        } else {
            for (int x = 0; x < bi.getWidth(); x++) {
                for (int y = 0; y < bi.getHeight(); y++) {
                    if (newlosdata.onMap(x + ovrRec.x, y + ovrRec.y)) {
                        int c = bi.getRGB(x, y);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Sand Dune, Low";
                            Terrain terr;
                            //Retrieving the R G B values
                            Color color = getRGBColor(c);
                            int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
                            if (terrint >= 0) {
                                terr = newlosdata.getTerrain(terrint);
                                if (terr.getName().equals("Dune, Crest Low")) {
                                    terraintouse = "Dune, Crest Low";
                                } else if (terr.getName().contains("Scrub")) {
                                    terraintouse = "Scrub";
                                }
                            }
                            newlosdata.setGridTerrainCode(newlosdata.getTerrain(terraintouse).getType(), x + ovrRec.x, y + ovrRec.y);
                            if (terraintouse.equals("Dune, Crest Low")) {
                                setDuneCrest(newlosdata, x, y, ovrRec, false);
                            }
                            newlosdata.gridToHex(x + ovrRec.x, y + ovrRec.y).getCenterLocation().setTerrain(newlosdata.getTerrain("Sand Dune, Low"));
                        }
                    }
                }
            }
        }

    }
    private void setDuneCrest(VASL.LOS.Map.Map newlosdata, int x, int y, Rectangle ovrRec, boolean isreversed){
        // reset the terrain
        Hex dunehex;
        Location dunecrestloc;
        if(isreversed){
            dunehex = newlosdata.gridToHex(x - ovrRec.x, y - ovrRec.y);
            dunecrestloc = dunehex.getNearestLocation(x - ovrRec.x, y - ovrRec.y);
        } else {
            dunehex = newlosdata.gridToHex(x + ovrRec.x, y + ovrRec.y);
            dunecrestloc = dunehex.getNearestLocation(x + ovrRec.x, y + ovrRec.y);

        }
        int hexside = dunehex.getLocationHexside(dunecrestloc);
        if (hexside != -1) {
            dunehex.setHexsideTerrain(hexside, newlosdata.getTerrain("Dune, Crest Low"));
            dunehex.setHexsideLocationTerrain(hexside, newlosdata.getTerrain("Dune, Crest Low"));
        }
    }
    private void setWadiTerrain(VASLBoard board, VASL.LOS.Map.Map newlosdata, BufferedImage bi, Rectangle ovrRec){
        if (board.isReversed()){
            // flip the overlay grid
            for (int x = 0; x < bi.getWidth(); x++) {
                for (int y = 0; y < bi.getHeight(); y++) {
                    if (newlosdata.onMap(newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1)) {
                        int c = bi.getRGB(x, y);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Open Ground";
                            Terrain terr;
                            //Retrieving the R G B values
                            Color color = getRGBColor(c);
                            int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
                            if (terrint >= 0) {
                                terr = newlosdata.getTerrain(terrint);
                                if (terr.getName().equals("Wadi")) {
                                    terraintouse = "Wadi";
                                } else if (terr.getName().equals("Cliff")) {
                                    terraintouse = "Cliff";
                                }
                            }
                            newlosdata.setGridTerrainCode(newlosdata.getTerrain(terraintouse).getType(), newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1);
                            if (terraintouse == "Wadi") {
                                newlosdata.gridToHex(newlosdata.getGridWidth() - ovrRec.x - x - 1, newlosdata.getGridHeight() - ovrRec.y - y - 1).getCenterLocation().setTerrain(newlosdata.getTerrain("Wadi"));
                                newlosdata.gridToHex(newlosdata.getGridWidth() - ovrRec.x - x - 1, newlosdata.getGridHeight() - ovrRec.y - y - 1).getCenterLocation().setBaseHeight(-1);
                            }
                        }
                    }
                }
            }
        } else {

            for (int x = 0; x < bi.getWidth(); x++) {
                for (int y = 0; y < bi.getHeight(); y++) {
                    if (newlosdata.onMap(x + ovrRec.x, y + ovrRec.y)) {
                        int c = bi.getRGB(x, y);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Open Ground";
                            Terrain terr;
                            //Retrieving the R G B values
                            Color color = getRGBColor(c);
                            int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
                            if (terrint >= 0) {
                                terr = newlosdata.getTerrain(terrint);
                                if (terr.getName().equals("Wadi")) {
                                    terraintouse = "Wadi";
                                } else if (terr.getName().equals("Cliff")) {
                                    terraintouse = "Cliff";
                                }
                            }
                            newlosdata.setGridTerrainCode(newlosdata.getTerrain(terraintouse).getType(), x + ovrRec.x, y + ovrRec.y);
                            if (terraintouse == "Wadi") {
                                newlosdata.gridToHex(x + ovrRec.x, y + ovrRec.y).getCenterLocation().setTerrain(newlosdata.getTerrain("Wadi"));
                                newlosdata.gridToHex(x + ovrRec.x, y + ovrRec.y).getCenterLocation().setBaseHeight(-1);
                                // need to set depression and cliff hexsides, but how?
                            }
                        }
                    }
                }
            }
        }

    }

    // this is the generic method for terrain overlays
    private void setOverlayTerrain(VASLBoard board, VASL.LOS.Map.Map newlosdata, BufferedImage bi, Rectangle ovrRec, String terraintype, boolean preserveelevation){
        // first test for inherent terrain type and send to separate method; use this method for non-inherent or mixed non-inherent/inherent overlays
        if (isInherenttype(terraintype)) {
            setOverlayInherentTerrain(board, newlosdata, bi, ovrRec, terraintype);
        } else {
            HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain>  inhHexes = new HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain>();
            HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain>  bdgHexes = new HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain>();
            int ovrx =0; int ovry=0;
            if (board.isReversed()){
                // flip the overlay grid
                for (int x = 0; x < bi.getWidth(); x++) {
                    for (int y = 0; y < bi.getHeight(); y++) {
                        int cropheight = board.getCropBounds().getHeight() == -1 ? (int) board.getUncroppedSize().getHeight() : (int)board.getCropBounds().getHeight();
                        int cropwidth = board.getCropBounds().getWidth() ==-1 ? (int) board.getUncroppedSize().getWidth() : (int) board.getCropBounds().getWidth() ;
                        ovrx = cropwidth - ovrRec.x  -x -1 + (int) board.getCropBounds().getX();
                        ovry = cropheight - ovrRec.y -y -1 + (int) board.getCropBounds().getY();
                        if (newlosdata.onMap(ovrx, ovry ) && newlosdata.gridToHex(ovrx, ovry) !=null) {
                            int c = bi.getRGB(x, y);
                            Terrain terr= null; int elevint=0;
                            if ((c >> 24) != 0x00) { // not a transparent pixel
                                //Retrieving the R G B values
                                Color color = getRGBColor(c);
                                terr = getTerrainfromColor(board,  color, newlosdata);
                                int bumpx =0; int bumpy=0;
                                while(terr==null){
                                    color = getnearestcolor(newlosdata, bi, ovrx + bumpx, ovry+ bumpy, x, y);
                                    if (color.equals(Color.white)){
                                        terr = newlosdata.getTerrain(board.getVASLBoardArchive().getTerrainForVASLColor("L0Winter"));
                                    } else {
                                        terr = getTerrainfromColor(board, color, newlosdata);
                                        if (terr ==null){bumpx+=1;bumpy+=1;}
                                    }
                                }
                                elevint = getElevationfromColor(board, color);
                                if (terr.isDepression()){
                                    elevint = newlosdata.getGridElevation(ovrx, ovry) -1;
                                }
                                //add Hex to collections of inherent hexes and building hexes on the overlay
                                addHextoInhandBldgMaps(terr, newlosdata, ovrx, ovry, inhHexes, bdgHexes);
                                //set terrain type for center location or hexside location (if hexside terrain)
                                setterraintype (newlosdata, ovrx, ovry, terr);
                                //set elevation level for point and hex
                                if (!preserveelevation) {
                                    // turn this into a method if can do so with reversed board
                                    //set elevation for point
                                    newlosdata.setGridElevation(elevint, ovrx, ovry );
                                    int testx =0; int testy =0;
                                    //adjust point values to match hexgrid data
                                    if ((int)board.getCropBounds().getX() ==0 && (int)board.getCropBounds().getWidth() != -1) {
                                        testx = ((int) board.getUncroppedSize().getWidth()) - ((int) board.getCropBounds().getWidth() - ovrx);
                                    } else {
                                        testx = ovrx;
                                    }
                                    if ((int)board.getCropBounds().getY() ==0 && (int)board.getCropBounds().getHeight()!=-1) {
                                        testy = (int) board.getUncroppedSize().getHeight() - ((int) board.getCropBounds().getHeight() - ovry);
                                    } else {
                                        testy = ovry;
                                    }
                                    //test if pixel is hex center
                                    if (testx  == (int)(newlosdata.gridToHex(ovrx, ovry).getCenterLocation().getLOSPoint()).getX() &&
                                        testy == (int)(newlosdata.gridToHex(ovrx, ovry).getCenterLocation().getLOSPoint()).getY()) {
                                        // if white center dot on overlay aligns with hex center, won't set elevation properly so need to look for nearby terrain type
                                        // bit of a hack but should work - try it until we get a bug
                                        color = getRGBColor(c);
                                        //int j =0; int k = 0;
                                        if (color.equals(Color.white) || color.equals(Color.black)){ // && j<=(x+6)) {
                                        //j += 2;        //k += 2;
                                            color = getnearestcolor(newlosdata, bi, ovrx, ovry, x, y);
                                            //int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
                                            if ((color.equals(Color.white))){
                                               elevint =0;
                                            } else {
                                               elevint = board.getVASLBoardArchive().getElevationForColor(color);
                                            }
                                        }
                                    }
                                    // this sets base elevation for the hex - crest line & depression hexes can contain multiple elevations
                                    newlosdata.gridToHex(ovrx, ovry).setBaseHeight(elevint);
                                }
                            } else {
                                // transparent pixel - check if center dot
                                //adjust point values to match hexgrid data
                                int testx =0; int testy =0;
                                if ((int)board.getCropBounds().getX() ==0 && (int)board.getCropBounds().getWidth() != -1) {
                                    testx = ((int) board.getUncroppedSize().getWidth()) - ((int) board.getCropBounds().getWidth() - ovrx);
                                } else{
                                    testx = ovrx;
                                }
                                if ((int)board.getCropBounds().getY() ==0 && (int)board.getCropBounds().getHeight()!=-1) {
                                    testy = (int) board.getUncroppedSize().getHeight() - ((int) board.getCropBounds().getHeight() - ovry);
                                } else {
                                    testy = ovry;
                                }
                                //test if pixel is hex center
                                if (ovrx + (int) board.getCropBounds().getX() == (int)(newlosdata.gridToHex(ovrx, ovry ).getHexCenter()).getX() &&
                                        ovry + (int) board.getCropBounds().getY()  == (int)(newlosdata.gridToHex(ovrx , ovry).getHexCenter()).getY()) {
                                    // if center dot on overlay is transparent and aligns with hex center, won't set elevation properly so need to look for nearby terrain type
                                    // bit of a hack but should work - try it until we get a bug
                                    // the bug is with overlays where the border is transparent so test
                                    // (1) if pixel is on the overlay edge and (2) if so are pixels 2 away also transparent
                                    // in those conditions, skip actions
                                    if (!pixelontransparentoverlayborder(newlosdata, bi, ovrx, ovry, x, y)) {

                                        //test if pixel is hex center
                                        //if (testx  == (int)(newlosdata.gridToHex(ovrx, ovry).getCenterLocation().getLOSPoint()).getX() &&
                                        //        testy == (int)(newlosdata.gridToHex(ovrx, ovry).getCenterLocation().getLOSPoint()).getY()) {

                                        int j = 0;
                                        int k = 0;
                                        elevint = -99;

                                        while ((c >> 24) == 0x00 && j <= 6) {
                                            j += 2;
                                            k += 2;
                                            if (newlosdata.onMap(x + j, y + k)) {
                                                c = bi.getRGB(x + j, y + k);
                                            } else if (newlosdata.onMap(x + j, y - k)) {
                                                c = bi.getRGB(x + j, y - k);
                                            } else if (newlosdata.onMap(x - j, y + k)) {
                                                c = bi.getRGB(x - j, y + k);
                                            } else if (newlosdata.onMap(x - j, y - k)) {
                                                c = bi.getRGB(x - j, y - k);
                                            } else {
                                                break;
                                            }
                                            Color color = getRGBColor(c);
                                            //int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
                                            if ((color.equals(Color.white))) {
                                                elevint = 0;
                                            } else {
                                                elevint = board.getVASLBoardArchive().getElevationForColor(color);
                                            }
                                            //if (terrint >= 0) {
                                            //    terr = newlosdata.getTerrain(terrint);
                                            //}
                                        }
                                        // this sets base elevation for the hex - crest line & depression hexes can contain multiple elevations
                                        if (elevint != -99) {
                                            newlosdata.gridToHex(ovrx, ovry).setBaseHeight(elevint);
                                        }

                                        /*while ((c >> 24) == 0x00 && j <= (x + 6)) {
                                            j += 2;
                                            k += 2;
                                            if (newlosdata.onMap(x + j, y + k)) {
                                                c = bi.getRGB(x + j, y + k);
                                            } else if (newlosdata.onMap(x + j, y - k)) {
                                                c = bi.getRGB(x + j, y - k);
                                            } else if (newlosdata.onMap(x - j, y + k)) {
                                                c = bi.getRGB(x - j, y + k);
                                            } else if (newlosdata.onMap(x - j, y - k)) {
                                                c = bi.getRGB(x - j, y - k);
                                            } else {
                                                break;
                                            }
                                            Color color = getRGBColor(c);
                                            //int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
                                            if ((color.equals(Color.white))) {
                                                elevint = 0;
                                            } else {
                                                elevint = board.getVASLBoardArchive().getElevationForColor(color);
                                            }
                                            //if (terrint >= 0) {
                                            //    terr = newlosdata.getTerrain(terrint);
                                            //}
                                        }
                                        // this sets base elevation for the hex - crest line & depression hexes can contain multiple elevations
                                        if (elevint != -99) {
                                            newlosdata.gridToHex(ovrx, ovry).setBaseHeight(elevint);
                                        }*/
                                    }
                                }
                            }
                        }
                    }
                }
                addinhterraintolos (inhHexes, newlosdata, board);
                addbldglevelstolos(bdgHexes, newlosdata, ovrRec, bi, board);
            } else {
                for (int x = 0; x < bi.getWidth(); x++) {
                    for (int y = 0; y < bi.getHeight(); y++) {
                        ovrx = x + (int) ovrRec.getX() - (int) board.getCropBounds().getX();
                        ovry = y + (int) ovrRec.getY() - (int) board.getCropBounds().getY();
                        if (newlosdata.onMap(ovrx, ovry) && newlosdata.gridToHex(ovrx, ovry) !=null) {
                            int c = bi.getRGB(x, y);
                            Terrain terr= null; int elevint=0;
                            if ((c >> 24) != 0x00) { // not a transparent pixel
                                //Retrieving the R G B values
                                Color color = getRGBColor(c);
                                terr = getTerrainfromColor(board,  color, newlosdata);
                                int bumpx =0; int bumpy=0;
                                while(terr==null){
                                    color = getnearestcolor(newlosdata, bi, ovrx + bumpx, ovry+ bumpy, x, y);
                                    if (color.equals(Color.white)){
                                        terr = newlosdata.getTerrain(board.getVASLBoardArchive().getTerrainForVASLColor("L0Winter"));
                                    } else {
                                        terr = getTerrainfromColor(board, color, newlosdata);
                                        if (terr ==null){bumpx+=1;bumpy+=1;}
                                    }
                                }
                                elevint = getElevationfromColor(board, color);
                                if (terr.isDepression()){
                                    elevint = newlosdata.getGridElevation(ovrx, ovry) -1;
                                }
                                //add Hex to collections of inherent hexes and building hexes on the overlay
                                addHextoInhandBldgMaps(terr, newlosdata, ovrx, ovry, inhHexes, bdgHexes);
                                //set terrain type for center location or hexside location (if hexside terrain)
                                setterraintype (newlosdata, ovrx, ovry, terr);

                                if (!preserveelevation) {
                                    // turn this into a method if can do so with reversed board
                                    //set elevation for point
                                    newlosdata.setGridElevation(elevint, ovrx, ovry );
                                    //test if pixel is hex center
                                    if (ovrx + (int) board.getCropBounds().getX() == (int)(newlosdata.gridToHex(ovrx, ovry ).getHexCenter()).getX() &&
                                    ovry + (int) board.getCropBounds().getY()  == (int)(newlosdata.gridToHex(ovrx , ovry).getHexCenter()).getY()) {
                                        // if white center dot on overlay aligns with hex center, won't set elevation properly so need to look for nearby terrain type
                                        // bit of a hack but should work - try it until we get a bug
                                        color = getRGBColor(c);
                                        //int j =0; int k = 0;
                                        if (color.equals(Color.white) || color.equals(Color.black)){ // && j<=(x+6)) {
                                            //j += 2;
                                            //k += 2;
                                            color = getnearestcolor(newlosdata, bi, ovrx, ovry, x, y);
                                            //int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
                                            if ((color.equals(Color.white))){
                                                elevint =0;
                                            } else {
                                                elevint = board.getVASLBoardArchive().getElevationForColor(color);
                                            }
                                            //if (terrint >= 0) {
                                            //    terr = newlosdata.getTerrain(terrint);
                                            //}
                                        }
                                        // this sets base elevation for the hex - crest line & depression hexes can contain multiple elevations
                                        // hack for LFT3; change if applies to other boards
                                        if (!board.getVASLBoardArchive().getVASLColorName(color).contains("SnowHexDots2")) {
                                            newlosdata.gridToHex(ovrx, ovry).setBaseHeight(elevint);
                                        }

                                    }
                                }
                            } else { // transparent pixel
                                //test if pixel is hex center
                                if (ovrx + (int) board.getCropBounds().getX() == (int)(newlosdata.gridToHex(ovrx, ovry ).getHexCenter()).getX() &&
                                        ovry + (int) board.getCropBounds().getY()  == (int)(newlosdata.gridToHex(ovrx , ovry).getHexCenter()).getY()) {
                                    // if center dot on overlay is transparent and aligns with hex center, won't set elevation properly so need to look for nearby terrain type
                                    // bit of a hack but should work - try it until we get a bug
                                    // the bug is with overlays where the border is transparent so test
                                    // (1) if pixel is on the overlay edge and (2) if so are pixels 2 away also transparent
                                    // in those conditions, skip actions
                                    if (!pixelontransparentoverlayborder(newlosdata, bi, ovrx, ovry, x, y)) {
                                        int j = 0;
                                        int k = 0;
                                        elevint = -99;
                                        while ((c >> 24) == 0x00 && j <= 6) {
                                            j += 2;
                                            k += 2;
                                            if (newlosdata.onMap(x + j, y + k)) {
                                                c = bi.getRGB(x + j, y + k);
                                            } else if (newlosdata.onMap(x + j, y - k)) {
                                                c = bi.getRGB(x + j, y - k);
                                            } else if (newlosdata.onMap(x - j, y + k)) {
                                                c = bi.getRGB(x - j, y + k);
                                            } else if (newlosdata.onMap(x - j, y - k)) {
                                                c = bi.getRGB(x - j, y - k);
                                            } else {
                                                break;
                                            }
                                            Color color = getRGBColor(c);
                                            //int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
                                            if ((color.equals(Color.white))) {
                                                elevint = 0;
                                            } else {
                                                elevint = board.getVASLBoardArchive().getElevationForColor(color);
                                            }
                                            //if (terrint >= 0) {
                                            //    terr = newlosdata.getTerrain(terrint);
                                            //}
                                        }
                                        // this sets base elevation for the hex - crest line & depression hexes can contain multiple elevations
                                        if (elevint != -99) {
                                            newlosdata.gridToHex(ovrx, ovry).setBaseHeight(elevint);
                                        }
                                    }
                                }

                            }
                        }
                    }
                }
                addinhterraintolos (inhHexes, newlosdata, board);
                addbldglevelstolos(bdgHexes, newlosdata, ovrRec, bi, board);
            }
        }
    }

    private void addinhterraintolos(HashMap<Hex, Terrain> inhHexes, VASL.LOS.Map.Map newlosdata, ASLBoard board) {
        try {
            for (Hex inhTerrHex : inhHexes.keySet()) {
                Integer terrType = inhHexes.get(inhTerrHex).getType();
                Rectangle s = inhTerrHex.getHexBorder().getBounds();
                for (int i = (int) s.getX(); i < s.getX() + s.getWidth(); i++) {
                    for (int j = (int) s.getY(); j < s.getY() + s.getHeight(); j++) {
                        if(newlosdata.onMap(i, j)) {
                            if (inhTerrHex.contains(i, j)) {
                                if (board.isReversed()) {
                                    int cropheight = board.getCropBounds().getHeight() == -1 ? (int) board.getUncroppedSize().getHeight() : (int) board.getCropBounds().getHeight();
                                    int cropwidth = board.getCropBounds().getWidth() == -1 ? (int) board.getUncroppedSize().getWidth() : (int) board.getCropBounds().getWidth();
                                    newlosdata.setGridTerrainCode(terrType, cropwidth - i, cropheight - j);
                                } else {
                                    newlosdata.setGridTerrainCode(terrType, i - (int) board.getCropBounds().getX(), j - (int) board.getCropBounds().getY());
                                }
                            }
                        }
                    }
                }
                inhTerrHex.getCenterLocation().setTerrain(newlosdata.getTerrain(terrType));
            }
        }catch (Exception e) {
            boolean reg =true;
        }
    }
    private void addbldglevelstolos(HashMap<Hex, Terrain> bdgHexes, VASL.LOS.Map.Map newlosdata, Rectangle ovrRec, BufferedImage bi, VASLBoard board){
        for(Hex bdglevelHex : bdgHexes.keySet()) {
            bdglevelHex.getCenterLocation().setTerrain(bdgHexes.get(bdglevelHex));
            Terrain centerLocationTerrain = bdglevelHex.getCenterLocation().getTerrain();
            boolean multihex = ismultihex(bdglevelHex, ovrRec, bi, board, newlosdata);
                bdglevelHex.addbuildinglevels(centerLocationTerrain, multihex);
        }
    }
    private boolean ismultihex(Hex bdglevelHex, Rectangle ovrRec, BufferedImage bi, VASLBoard board, VASL.LOS.Map.Map newlosdata){
        boolean multihexbdg = false;
        // find where on overlay the hex is centered
        Point hexcentreonoverlay = new Point();
        hexcentreonoverlay.x = (int) (bdglevelHex.getHexCenter().getX() - ovrRec.getX());
        hexcentreonoverlay.y = (int) (bdglevelHex.getHexCenter().getY() - ovrRec.getY());
        // use hex center to test if hexsides contain building pixels
        final double verticalOffset = bdglevelHex.getMap().getHexHeight()/2.0;
        // the hexside point is the hexside center point translated one pixel toward the hex center point
        // [0] is the top hexside and the other points are clock-wise from there
        Point [] hexsidePoints = new Point[6];
        final double horizontalOffset = cos(Math.toRadians(30.0)) * verticalOffset;

        hexsidePoints[0] = new Point ((hexcentreonoverlay.x), (int) (-verticalOffset + hexcentreonoverlay.y + 1.0));
        hexsidePoints[1] = new Point ((int)(horizontalOffset + hexcentreonoverlay.x - 1),  (int) (-verticalOffset/2.0 + hexcentreonoverlay.y + 1.0));
        hexsidePoints[2] = new Point ((int)(horizontalOffset + hexcentreonoverlay.x - 1),  (int) (verticalOffset/2.0 + hexcentreonoverlay.y - 1.0));
        hexsidePoints[3] = new Point (hexcentreonoverlay.x, (int) (verticalOffset + hexcentreonoverlay.y - 1.0));
        hexsidePoints[4] = new Point ((int) (-horizontalOffset + hexcentreonoverlay.x + 1),  (int) (verticalOffset/2.0 + hexcentreonoverlay.y - 1.0));
        hexsidePoints[5] = new Point ((int) (-horizontalOffset + hexcentreonoverlay.x + 1),  (int) (-verticalOffset/2.0 + hexcentreonoverlay.y + 1.0));
        // now test if hexside points contain building colour; if the do, it is multihex building
        for (int i =0; i < 6; i++) {
            int c = bi.getRGB((int) hexsidePoints[i].getX(), (int) hexsidePoints[i].getY());
            Terrain terr= null;
            if ((c >> 24) != 0x00) { // not a transparent pixel
                String terraintouse = "Open Ground";
                //Retrieving the R G B values
                Color color = getRGBColor(c);
                int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
                if (terrint >= 0) {
                    terr = newlosdata.getTerrain(terrint);
                    terraintouse = terr.getName();
                }
                if (terr != null) {
                    if (terr.isBuilding()) {
                        multihexbdg=true;
                        break;
                    }
                }
            }
        }
        return multihexbdg;
    }
    //set terrain type for center location or hexside location (if hexside terrain)
    private void setterraintype (VASL.LOS.Map.Map newlosdata, int ovrx, int ovry, Terrain terr) {
        newlosdata.setGridTerrainCode(terr.getType(), ovrx, ovry);
        if (newlosdata.gridToHex(ovrx, ovry).getNearestLocation(ovrx, ovry).isCenterLocation()) {
            newlosdata.gridToHex(ovrx, ovry).getCenterLocation().setTerrain(terr);
        } else if (terr != null && terr.isHexsideTerrain()) {
            int hexside = newlosdata.gridToHex(ovrx, ovry).getLocationHexside(newlosdata.gridToHex(ovrx, ovry).getNearestLocation(ovrx, ovry));
            Point hexsidecenter = newlosdata.gridToHex(ovrx, ovry).getHexsideLocation(hexside).getEdgeCenterPoint();
            //only set hexside terrain for hex and hexside location if within 10 pixels of hexside centre - avoids mistaken hexsides
            if (Math.abs(ovrx - hexsidecenter.x) < 10 && Math.abs(ovry - hexsidecenter.y) < 10) {
                newlosdata.gridToHex(ovrx, ovry).setHexsideTerrain(hexside, terr);
                newlosdata.gridToHex(ovrx, ovry).setHexsideLocationTerrain(hexside, terr);
            }
        }
    }
    private Terrain getTerrainfromColor(VASLBoard board, Color color, VASL.LOS.Map.Map newlosdata){
        int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
        if (terrint >= 0) {
            return newlosdata.getTerrain(terrint);
        }
        return null; //newlosdata.getTerrain("Open Ground");
    }
    private Integer getElevationfromColor(VASLBoard board, Color color){
        int elevint = board.getVASLBoardArchive().getElevationForColor(color);
        if (elevint == -99){elevint=0;}
        return elevint;
    }
    private Color getnearestcolor(VASL.LOS.Map.Map newlosdata, BufferedImage bi, int ovrx, int ovry, int x, int y){
        int c = 0; int a=2;
        Color color = Color.BLACK;
        while (color.equals(Color.BLACK)) {
            // point must be (a) on map (b) on overlay (c) not transparent
            if ((newlosdata.onMap(ovrx + a, ovry + a)) && (pointIsOnOverlay(bi, x+(a-1), y+a) && (!((bi.getRGB(x+(a-1), y+a) >> 24) == 0X00)))) {
                c = bi.getRGB(x + (a - 1), y + a);
            } else if ((newlosdata.onMap(ovrx + a, ovry - a)) && (pointIsOnOverlay(bi, x+(a-1), y-a) && (!((bi.getRGB(x+(a-1), y-a) >> 24) == 0X00)))) {
                c = bi.getRGB(x + (a - 1), y - a);
            } else if ((newlosdata.onMap(ovrx - a, ovry + a)) && (pointIsOnOverlay(bi, x-(a-1), y+a) && (!((bi.getRGB(x-(a-1), y+a) >> 24) == 0X00)))) {
                c = bi.getRGB(x - (a - 1), y + a);
            } else if ((newlosdata.onMap(ovrx - a, ovry - a)) && (pointIsOnOverlay(bi, x-(a-1), y-a) && (!((bi.getRGB(x-(a-1), y-a) >> 24) == 0X00)))) {
                c = bi.getRGB(x - (a - 1), y - a);
            } else {
                return null;
            }



            /*if (newlosdata.onMap(ovrx + a, ovry + a) && (x+(a-1)<bi.getWidth() && y+a <bi.getHeight())) {
                c = bi.getRGB(x + (a - 1), y + a);
                if ((c >> 24) == 0x00) { // a transparent pixel
                    if (newlosdata.onMap(ovrx + a, ovry - a)) {
                        c = bi.getRGB(x + (a - 1), y - a);
                        if ((c >> 24) == 0x00) { // a transparent pixel
                            if (newlosdata.onMap(ovrx - a, ovry + a)) {
                                c = bi.getRGB(x - (a - 1), y + a);
                                if ((c >> 24) == 0x00) { // a transparent pixel
                                    if (newlosdata.onMap(ovrx - a, ovry - a)) {
                                        c = bi.getRGB(x - (a - 1), y - a);
                                        if ((c >> 24) == 0x00) { // a transparent pixel
                                            return null;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }*/

            color = getRGBColor(c);
            a+=1;
        }
        return color;
    }
    private Boolean pixelontransparentoverlayborder(VASL.LOS.Map.Map newlosdata, BufferedImage bi, int ovrx, int ovry, int x, int y){
        int c = 0;
        int b = 0;
        int a = 3;
        if (x==0 || x== bi.getWidth()-1) {
            if (newlosdata.onMap(ovrx, ovry + a)) {
                c = bi.getRGB(x, y + a);
                if ((c >> 24) != 0x00) { // not a transparent pixel
                    return false;
                }
            }
            if (newlosdata.onMap(ovrx, ovry - a)) {
                b = bi.getRGB(x, y - a);
                if ((b >> 24) != 0x00) { // not a transparent pixel
                    return false;
                }
            }
            return true; //transparent pixel
        } else if (y==0 || y == bi.getHeight()-1){
            if (newlosdata.onMap(ovrx + a, ovry)) {
                c = bi.getRGB(x + a, y);
                if ((c >> 24) != 0x00) { // not a transparent pixel
                    return false;
                }
            }
            if (newlosdata.onMap(ovrx -a, ovry)) {
                b = bi.getRGB(x-a, y);
                if ((b >> 24) != 0x00) { // not a transparent pixel
                    return false;
                }
            }
            return true; //transparent pixel
        }
        return false;  // not on border
    }
    private boolean pointIsOnOverlay(BufferedImage bi, int usex, int usey){
        return usex >= 0 && usex < bi.getWidth() && usey >= 0 && usey < bi.getHeight();
    }
    //add Hex to collections of inherent hexes and building hexes on the overlay
    private void addHextoInhandBldgMaps(Terrain terr, VASL.LOS.Map.Map newlosdata, double ovrx, double ovry, HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain>  inhHexes, HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain> bdgHexes) {

        if (terr != null) {
            if (terr.isInherentTerrain()) {
                if (!inhHexes.containsKey(newlosdata.gridToHex((int) ovrx, (int) ovry))) {
                    //hack - ensure that the pixel is not close to a hexside as VASL geometry can put it in an adjacent hex
                    Point hexcenter =newlosdata.gridToHex((int) ovrx, (int) ovry).getHexCenter();
                    Double d=  Math.sqrt(((Math.pow(hexcenter.x - ovrx, 2) + (Math.pow(hexcenter.y - ovry, 2)))));
                    if (d <25) {
                        inhHexes.put(newlosdata.gridToHex((int) ovrx, (int) ovry), terr);
                    }
                }
            } else if (terr.isBuilding()) {
                if (!terr.getName().equals("Stone Building") && !terr.getName().equals("Wooden Building") && !terr.getName().contains("Rowhouse Wall")) {
                    if (!bdgHexes.containsKey(newlosdata.gridToHex((int) ovrx, (int) ovry))) {
                        bdgHexes.put(newlosdata.gridToHex((int) ovrx, (int) ovry), terr);
                    }
                }
            }
        }
    }

    // if overlayname returns "" from this method then los checking won't work with the overlay
    // when adding items here also add them to VASLThread.initializeMap
    private String getoverlayterraintype(Overlay o){
        String overlayname = o.getName();
        if (overlayname.contains("b")){
            return "Brush";
        }
        if (overlayname.contains("hd")){
            return "Hedges";
        }
        if (overlayname.contains("sh")){
            return "Shellholes";
        }
        else if (overlayname.contains("og") || overlayname.equals("dx1") || overlayname.equals("dx5")){
            return "Open Ground";
        }
        else if (overlayname.contains("m")){
            return "Marsh";
        }
        else if (overlayname.contains("g")){
            return "Grain";
        }
        else if (overlayname.contains("p")){
            return "Water";
        }
        else if (overlayname.contains("ow")){
            return "Woods";
        }
        else if (overlayname.contains("o") || overlayname.equals("dx3") || overlayname.equals("dx7")){
            return "Orchard";
        }
        else if (overlayname.contains("wd") || overlayname.equals("dx2") || overlayname.equals("dx4")){
            return "Woods";
        }
        else if (overlayname.contains("x") && !overlayname.contains("dx")) {
            return "Building";
        }
        else if (overlayname.contains("hi")) {
            return "Hill";
        }
        else if (overlayname.contains("st")) {
            return "Stream";
        }
        else {
            return "";
        }

    }

    private boolean isInherenttype(String terraintype){
        return terraintype.equals("Orchard");
    }
    private void setOverlayInherentTerrain(VASLBoard board, VASL.LOS.Map.Map newlosdata, BufferedImage bi, Rectangle ovrRec, String terraintype){
        Hex temphex = null; Hex newhex;
        Hex previoushex = null;

        if (board.isReversed()){
            // flip the overlay grid
            for (int x = 0; x < bi.getWidth(); x++) {
                for (int y = 0; y < bi.getHeight(); y++) {
                    if (newlosdata.onMap(newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1)) {
                        Hex hextouse = newlosdata.gridToHex(newlosdata.getGridWidth() - ovrRec.x -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1);
                        if (!hextouse.equals(previoushex)) {
                            int c = bi.getRGB(x, y);
                            if ((c >> 24) != 0x00) { // not a transparent pixel
                                String terraintouse = "Open Ground";
                                Terrain terr = null;
                                //Retrieving the R G B values
                                Color color = getRGBColor(c);
                                int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
                                if (terrint >= 0) {
                                    terr = newlosdata.getTerrain(terrint);
                                    if (terr.getName().equals(terraintype)) {
                                        terraintouse = terraintype;
                                    }
                                }
                                if (!terraintouse.equals("Open Ground") && terr != null) {  // terrain is inherent terrain

                                    hextouse.getCenterLocation().setTerrain(newlosdata.getTerrain(terraintouse));
                                    hextouse.setoverlayborder();
                                    LOSDataEditor loseditor = new LOSDataEditor(newlosdata);
                                    loseditor.setGridTerrain(hextouse.getoverlayborder(), terr);
                                    for (int z = 0; z < 6; z++) {
                                        hextouse.setHexsideTerrain(z, newlosdata.getTerrain("Open Ground"));
                                        Hex adjhex = newlosdata.getAdjacentHex(hextouse, z);
                                        if (adjhex != null) {
                                            adjhex.setHexsideTerrain(Hex.getOppositeHexside(z), newlosdata.getTerrain("Open Ground"));
                                        }
                                    }
                                    previoushex = hextouse;
                                }
                            }

                            //newlosdata.setGridTerrainCode(newlosdata.getTerrain(terraintouse).getType(), newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1);
                            //if (newlosdata.gridToHex(newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1).getNearestLocation(newlosdata.getGridWidth() - ovrRec.x  -y -1, newlosdata.getGridHeight() - ovrRec.y -x -1).isCenterLocation()) {
                            //    newlosdata.gridToHex(newlosdata.getGridWidth() - ovrRec.x  -x -1, newlosdata.getGridHeight() - ovrRec.y -y -1).getCenterLocation().setTerrain(newlosdata.getTerrain(terraintouse));
                            //}
                        }
                    }
                }
            }
        } else {
            for (int x = 0; x < bi.getWidth(); x++) {
                for (int y = 0; y < bi.getHeight(); y++) {
                    if (newlosdata.onMap(x + ovrRec.x, y + ovrRec.y)) {
                        Hex hextouse = newlosdata.gridToHex(x + ovrRec.x, y + ovrRec.y);
                        if (!hextouse.equals(previoushex)) {
                            int c = bi.getRGB(x, y);
                            if ((c >> 24) != 0x00) { // not a transparent pixel
                                String terraintouse = "Open Ground";
                                Terrain terr = null;
                                //Retrieving the R G B values
                                Color color = getRGBColor(c);
                                int terrint = board.getVASLBoardArchive().getTerrainForColor(color);
                                if (terrint >= 0) {
                                    terr = newlosdata.getTerrain(terrint);
                                    if (terr.getName().equals(terraintype)) {
                                        terraintouse = terraintype;
                                    }
                                }

                                if (!terraintouse.equals("Open Ground") && terr != null) {  // terrain is inherent terrain

                                    hextouse.getCenterLocation().setTerrain(newlosdata.getTerrain(terraintouse));
                                    hextouse.setoverlayborder();
                                    LOSDataEditor loseditor = new LOSDataEditor(newlosdata);
                                    loseditor.setGridTerrain(hextouse.getoverlayborder(), terr);
                                    for (int z = 0; z < 6; z++) {
                                        hextouse.setHexsideTerrain(z, newlosdata.getTerrain("Open Ground"));
                                        Hex adjhex = newlosdata.getAdjacentHex(hextouse, z);
                                        if (adjhex != null) {
                                            adjhex.setHexsideTerrain(Hex.getOppositeHexside(z), newlosdata.getTerrain("Open Ground"));
                                        }
                                    }
                                    previoushex = hextouse;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    /**
     * Sets status of LOS engine to legacy mode
     */
    private void setLegacyMode() {
        legacyMode = true;
        VASLMap = null;

    }
    /**
     * @return the VASL map
     */
    public VASL.LOS.Map.Map getVASLMap(){
        return VASLMap;
    }

    /**
     * @return the shared board metadata
     */
    public static SharedBoardMetadata getSharedBoardMetadata() {
        return sharedBoardMetadata;
    }

    /**
     * @return the counter metadata
     */
    public static CounterMetadataFile getCounterMetadata() {
        return counterMetadata;
    }

    /**
     * @return true if the map is in legacy mode (i.e. pre-6.0)
     */
    public boolean isLegacyMode(){
        return legacyMode;
    }

    /**
     * Log a string to the VASSAL error log
     * @param error the error string
     */
    private void logError(String error) {
        logger.info(error);
    }

    /**
     * Log an exception to the VASSAL error log
     * @param error the exception
     */
    private void logException(Throwable error) {
        logger.info("", error);
    }

    public BufferedImage getImgMapIcon(Point pt, double size, double os_scale) {
        BufferedImage img = new BufferedImage((int)size, (int)size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2d = img.createGraphics();

        double dzoom = 0.0;
        Rectangle rect = null;

        for (Board b: getBoards()) {
            if (rect == null) {
                final double mag = b.getMagnification();
                dzoom = os_scale / mag;

                rect = new Rectangle(
                  (int)((pt.x * os_scale - size/2) / mag),
                  (int)((pt.y * os_scale - size/2) / mag),
                  (int)(size / mag),
                  (int)(size / mag)
                );

                g2d.translate(-rect.x, -rect.y);
            }

            b.drawRegion(g2d, getLocation(b, dzoom), rect, dzoom, null);
        }

        drawPiecesNonStackableInRegion(g2d, rect, dzoom);

        g2d.dispose();
        return img;
    }

  protected void drawPiecesNonStackableInRegion(Graphics g, Rectangle visibleRect, double dZoom)
  {
      Graphics2D g2d = (Graphics2D) g;
      Composite oldComposite = g2d.getComposite();
      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pieceOpacity));
      
      GamePiece[] stack = pieces.getPieces();
      
      for (int i = 0; i < stack.length; ++i) 
      {
        Point pt = stack[i].getPosition();
        
        if (stack[i].getClass() != Stack.class) 
        {
          if (Boolean.TRUE.equals(stack[i].getProperty(Properties.NO_STACK))) 
              stack[i].draw(g, (int)(pt.x * dZoom), (int)(pt.y * dZoom), null, dZoom);
        }
      }
      
      g2d.setComposite(oldComposite);
  }    
  
  public void setShowMapLevel(ShowMapLevel showMapLevel) {
      m_showMapLevel = showMapLevel;
  }
    
    @Override
  public boolean isPiecesVisible() {
    return pieceOpacity != 0;
  }   
  
    @Override
    public void drawPiecesInRegion(Graphics g,
                                 Rectangle visibleRect,
                                 Component c) {

    Graphics2D g2d = (Graphics2D) g;
    Composite oldComposite = g2d.getComposite();
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pieceOpacity));

    final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();
    final double dzoom = getZoom() * os_scale;

    GamePiece[] stack = pieces.getPieces();

    for (int i = 0; i < stack.length; ++i)
    {
        Point pt = mapToDrawing(stack[i].getPosition(), os_scale);

        if (stack[i].getClass() == Stack.class)
        {
            if (m_showMapLevel == ShowMapLevel.ShowAll)
                getStackMetrics().draw((Stack) stack[i], pt, g, this, dzoom, visibleRect);
        }
        else
        {
            if (m_showMapLevel == ShowMapLevel.ShowAll  || (stack[i].getProperty("overlay") != null && m_showMapLevel == ShowMapLevel.ShowMapOnly)) // always show overlays
            {
                stack[i].draw(g, pt.x, pt.y, c, dzoom);

                if (Boolean.TRUE.equals(stack[i].getProperty(Properties.SELECTED)))
                    highlighter.draw(stack[i], g, pt.x, pt.y, c, dzoom);
            }
            else if (m_showMapLevel == ShowMapLevel.ShowMapAndOverlay)
            {
                if (Boolean.TRUE.equals(stack[i].getProperty(Properties.NO_STACK)))
                {
                    stack[i].draw(g, pt.x, pt.y, c, dzoom);

                    if (Boolean.TRUE.equals(stack[i].getProperty(Properties.SELECTED)))
                        highlighter.draw(stack[i], g, pt.x, pt.y, c, dzoom);
                }
            }
        }
/*
        // draw bounding box for debugging
        final Rectangle bb = stack[i].boundingBox();
        g.drawRect(pt.x + bb.x, pt.y + bb.y, bb.width, bb.height);
*/
        }
        
        g2d.setComposite(oldComposite); 
    }

    @Override
  public void drawPieces(Graphics g, int xOffset, int yOffset) 
  {

    Graphics2D g2d = (Graphics2D) g;
    Composite oldComposite = g2d.getComposite();
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pieceOpacity));

    final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();
    final double dzoom = getZoom() * os_scale;

    GamePiece[] stack = pieces.getPieces();

    for (int i = 0; i < stack.length; ++i)
    {
        if (m_showMapLevel == ShowMapLevel.ShowAll || (stack[i].getProperty("overlay") != null && m_showMapLevel == ShowMapLevel.ShowMapOnly)) // always show overlays
        {
            Point pt = mapToDrawing(stack[i].getPosition(), os_scale);

            stack[i].draw(g, pt.x + xOffset, pt.y + yOffset, theMap, dzoom);

            if (Boolean.TRUE.equals(stack[i].getProperty(Properties.SELECTED)))
                highlighter.draw(stack[i], g, pt.x - xOffset, pt.y - yOffset, theMap, dzoom);
        }
        else if (m_showMapLevel == ShowMapLevel.ShowMapAndOverlay)
        {
            if (stack[i].getClass() != Stack.class)
            {
                if (Boolean.TRUE.equals(stack[i].getProperty(Properties.NO_STACK)))
                {
                    Point pt = mapToDrawing(stack[i].getPosition(), os_scale);

                    stack[i].draw(g, pt.x + xOffset, pt.y + yOffset, theMap, dzoom);

                    if (Boolean.TRUE.equals(stack[i].getProperty(Properties.SELECTED)))
                        highlighter.draw(stack[i], g, pt.x - xOffset, pt.y - yOffset, theMap, dzoom);
                }
            }
        }
    }

    g2d.setComposite(oldComposite);
  }
    private Color getRGBColor(int c){
        int red = (c & 0x00ff0000) >> 16;
        int green = (c & 0x0000ff00) >> 8;
        int blue = c & 0x000000ff;
        return new Color(red, green, blue);
    }
  
  public enum ShowMapLevel
  {
      ShowAll,
      ShowMapAndOverlay,
      ShowMapOnly        
  }
}
