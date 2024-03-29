package controller;

import database.ArtikelDBContext;
import model.Artikel;
import model.KortingFactory;
import model.VerkoopModel;
import model.decorator.KassaTicket;
import model.decorator.KassaTicketDecoratorFooter;
import model.decorator.KassaTicketDecoratorHeader;
import model.decorator.KassaTicketImplementation;
import model.observer.Observer;
//import model.state.*;
import model.strategy.KortingStrategy;
import view.KassaView;
import view.KlantView;
import view.panels.*;

import java.io.*;
import java.time.LocalDate;
import java.util.*;

public class VerkoopController implements Observer {
    private ArtikelDBContext context;
    private VerkoopModel verkoopModel;
    private int verkoopTeller = 0;

    private VerkoopPane verkoopPane;
    private KlantViewPane klantViewPane;
    private PropertiesPane propertiesPane;
    private ProductOverviewPane productOverviewPane;

    private KassaView kassaView;
    private KlantView klantView;
    private LogPane logPane;

    private KassaTicket ticket = new KassaTicketImplementation();

    private Properties properties;
    private KortingStrategy kortingStrategy;
   // private ProductOverviewPane productOverviewPane;

    public VerkoopController() {

        this.verkoopModel = new VerkoopModel(this);
        verkoopModel.registerObserver(this);
        this.context = new ArtikelDBContext();


        this.logPane = new LogPane(this);
        this.propertiesPane = new PropertiesPane();
        this.klantViewPane = new KlantViewPane(this);
        this.klantView = new KlantView(this, klantViewPane);
        this.verkoopPane = new VerkoopPane(this);
        this.productOverviewPane = new ProductOverviewPane(this);
        this.properties = propertiesPane.getInstellingen();
        this.kassaView = new KassaView(this, context, productOverviewPane, propertiesPane ,properties);
    }

    public void updateVoorraad(int aantal, Artikel a) {
        if(a == null) {
            throw new IllegalArgumentException("Artikel mag niet leeg zijn");
        }
        context.add(a.getCode()).setInVoorraad(aantal);
    }

    public void printKassaTicket() {
        String header = properties.getProperty("header");
        String footer = properties.getProperty("footer");
        if(header != null) {
            ticket = new KassaTicketDecoratorHeader(ticket, header);
            removeHeaderFooter();
        }

        if(footer != null) {
            ticket = new KassaTicketDecoratorFooter(ticket, footer);
            removeHeaderFooter();
        }
        System.out.println(ticket.print(verkoopModel.getArtikelen(), verkoopModel.getTotalePrijs()-getKorting()));
    }

    private void removeHeaderFooter() {
        try {
            OutputStream os = new FileOutputStream("src/bestanden/instellingen.properties");
            if(properties.getProperty("header") != null){
                properties.remove("header");
            }

            if(properties.getProperty("footer") != null) {
                properties.remove("footer");
            }
            properties.store(os, "Instellingen van de KassaApp");
        } catch (IOException e) {
            throw new ControllerException(e.getMessage());
        }
    }



    public void slaVerkoopOp() {
        try(FileOutputStream fileOut = new FileOutputStream("src/bestanden/verkoop.ser");
            ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            verkoopModel.getCurrentState().onHold();
            out.writeObject(this.verkoopModel.getArtikelen());
            //Testing
            //System.out.println("Verkoop on hold gezet");
            //verkoopModel.clearArtikelen();

            this.verkoopModel = new VerkoopModel(this);
            update(null, this.verkoopModel.getArtikelen(),0,false);
            //verkoopModel.volgendeVerkoop();
        } catch (IOException e) {
            throw new ControllerException(e.getMessage());
        }/*
        this.onHoldVerkoop = this.verkoopModel;
        this.verkoopModel = new VerkoopModel(this);
        update(null, this.verkoopModel.getArtikelen(),0,false);*/
    }

    public void leesVerkoop() {
        VerkoopModel verkoop;
        if(verkoopTeller <= 3) {
            this.verkoopModel.getCurrentState().onHold();
            this.verkoopModel.getCurrentState().zetNietOnHold();
            try (FileInputStream fileIn = new FileInputStream("src/bestanden/verkoop.ser");
                 ObjectInputStream in = new ObjectInputStream(fileIn)) {
                ArrayList<Artikel> artikelArrayList = (ArrayList<Artikel>) in.readObject();
                //verkoop = new VerkoopModel(this);
                verkoopModel.clearArtikelen();
                for (Artikel artikel : artikelArrayList) {
                    verkoopModel.addArtikel(artikel);
                }
                //this.verkoopModel = verkoop;
                update(null, this.verkoopModel.getArtikelen(), 0, false);

            } catch (IOException | ClassNotFoundException e) {
                throw new ControllerException(e.getMessage());
            }
        } else {
            File verkoopFile = new File("src/bestanden/verkoop.ser");
            verkoopFile.delete();
            verkoopTeller = 0;
        }
        /*
        this.verkoopModel = this.onHoldVerkoop;
        this.onHoldVerkoop = null;
        update(null, this.verkoopModel.getArtikelen(),0,false);*/
        //System.out.println(verkoopModel.getCurrentState());
    }
    public List<Artikel> convertToCustomerView(List<Artikel> artikelArrayList) {
        List<Artikel> result = new ArrayList<>();
        List<Artikel> artikels = artikelArrayList;
        HashMap<Integer, Integer> artikelHashMap = new HashMap<>();
        for (Artikel item : artikels)
            if (artikelHashMap.containsKey(item.getCode())) {
                artikelHashMap.put(item.getCode(), artikelHashMap.get(item.getCode()) + 1);
            } else
                artikelHashMap.put(item.getCode(), 1);

        for (Map.Entry hashmap : artikelHashMap.entrySet()) {
            if (hashmap.getKey() != null) {
                Artikel a = context.get((Integer) hashmap.getKey());
                Artikel artikel = new Artikel(a.getCode(), a.getOmschrijving(), a.getArtikelGroep(), a.getVerkoopprijs(), a.getInVoorraad());
                artikel.setAantalPerKeer((Integer) hashmap.getValue());
                result.add(artikel);
            }
        }
        return result;
    }

    public VerkoopPane getVerkoopPane() {
        return verkoopPane;
    }

    public void setKlantViewPane(KlantViewPane klantViewPane) {
        if(klantViewPane == null) {
            throw new IllegalArgumentException("KlantView mag niet leeg zijn");
        }
        this.klantViewPane = klantViewPane;
    }
    public void codeEnter(int code) {
        Artikel a = context.add(code);
        if(a != null) {
            verkoopModel.addArtikel(a);
            update(a, verkoopModel.getArtikelen(), getKorting(), false);
            verkoopPane.artikelWelGevonden();
        } else {

            update(a, verkoopModel.getArtikelen(), getKorting(), false);
            verkoopPane.artikelNietGevonden();
        }
    }

    public void codeRemove(int code) {
        Artikel a = context.add(code);
        if(a != null && verkoopModel.getArtikelen().contains(a)) {
            verkoopModel.removeArtikel(a);
            update(a, verkoopModel.getArtikelen(), getKorting(), verkoopPane.getAfgesloten());
            verkoopPane.artikelWelGevonden();
        } else {
            verkoopPane.artikelNietGevonden();
            update(a, verkoopModel.getArtikelen(), getKorting(), verkoopPane.getAfgesloten());
        }
    }
    public void update(){
        update(null, verkoopModel.getArtikelen(), getKorting(), verkoopPane.getAfgesloten());
    }

    public double getKorting() {
        String type = properties.getProperty("kortingsType");
        String groep = properties.getProperty("groep");
        //System.out.println(groep);
        int percentage = Integer.parseInt(properties.getProperty("percentage"));
        double minBedrag = Double.parseDouble(properties.getProperty("bedrag"));
        this.kortingStrategy = KortingFactory.createStrategy(type);
        //System.out.println(kortingStrategy);
        //System.out.println(this.kortingStrategy.berekenKorting(verkoopModel.getArtikelen(), groep, percentage, minBedrag));
        return this.kortingStrategy.berekenKorting(verkoopModel.getArtikelen(), groep, percentage, minBedrag);
    }
//    public void afsluit(){
//        klantViewPane.afsluit();
//    }

    public void betaalVerkoop() {
        verkoopModel.getCurrentState().betaal();
    }

    public void annuleerVerkoop() {
        verkoopModel.getCurrentState().annuleer();
        update(null, new ArrayList<>(),0,true);

    }
    public void setProductOverviewPane(ProductOverviewPane productOverviewPane){
        this.productOverviewPane = productOverviewPane;
    }

    public void resetVerkoop() {
        verkoopModel.getCurrentState().reset();
        update(null, new ArrayList<>(),0,true);
        verkoopPane.setAfsluit(false);
        verkoopPane.setInputField(false);
        verkoopModel.clearArtikelen();
        verkoopPane.resetLabels();
        klantViewPane.resetLabels();


    }

    public void eindigVerkoop() {
            verkoopModel.getCurrentState().betaal();
            pasVoorraadAan();
            productOverviewPane.updateDisplay(context.getAll());
            this.printKassaTicket();
            this.logPane.addEntry(createEntry(LocalDate.now(),verkoopModel.getTotalePrijs(), getKorting(), verkoopModel.getTotalePrijs()-getKorting()));
            update(null, new ArrayList<>(),0,true);
            verkoopPane.setAfsluit(false);
            verkoopPane.setInputField(false);
            verkoopModel.clearArtikelen();
            verkoopPane.resetLabels();
            klantViewPane.resetLabels();
            //context.save(context.getAll());
            //productOverviewPane.updateDisplay(context.load());
            verkoopTeller++;



    }

    private void pasVoorraadAan() {
        for(Artikel a : verkoopModel.getArtikelen()) {
            for(Artikel artikel : context.getAll()) {
                if(a.equals(artikel)) {
                    artikel.setInVoorraad(artikel.getInVoorraad()-a.getAantalPerKeer());
                    context.updateArtikel(artikel);
                }
            }
        }
        //context.save(context.getAll());
    }

    public LogPane getLogPane() {
        return logPane;
    }

    public String createEntry(LocalDate datum, double totaalBedrag, double korting, double teBetalen) {
        return datum + " - " + totaalBedrag + " - " + korting + " - " + teBetalen;
    }

    public void sluitVerkoopAf() {
        this.verkoopModel.getCurrentState().sluitAf();

    }


    @Override
    public void update(Artikel a, List<Artikel> artikelen, double korting, boolean afgesloten) {
        double totalePrijs = verkoopModel.getTotalePrijs();

        verkoopPane.updateDisplay(totalePrijs, artikelen, korting, afgesloten);
        klantViewPane.updateDisplay(totalePrijs, artikelen, korting, afgesloten);
        //productOverviewPane.updateDisplay(artikelen);


    }
}
