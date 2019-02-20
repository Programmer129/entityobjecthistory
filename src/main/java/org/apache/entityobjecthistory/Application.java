package org.apache.entityobjecthistory;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.util.Date;

public class Application {

    public static void main(String[] args) throws InstantiationException, IllegalAccessException {
        Model model = new Model();
        model.setName("avto");

        SubModel subModel = new SubModel();
        subModel.setId(2);
        subModel.setName("subModel");

        model.setSubModel(subModel);

        EntityObjectHistoryContainerUtil.updateHistoryFile(1, model, "/home/avto/model.json");

        Object o = EntityObjectHistoryContainerUtil.loadHistoryFile(new Date(), "/home/avto/model.json", Model.class);

        System.out.println(o);
    }
}

@Entity
class Model {

    @Id
    private int id;
    private String name;

    public SubModel getSubModel() {
        return subModel;
    }

    public void setSubModel(SubModel subModel) {
        this.subModel = subModel;
    }

    @ManyToOne
    private SubModel subModel = new SubModel();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

@Entity
class SubModel {
    @Id
    private int id;
    private String name;

    public SubModel() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
