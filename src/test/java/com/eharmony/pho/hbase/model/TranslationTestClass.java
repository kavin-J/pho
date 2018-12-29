package com.eharmony.pho.hbase.model;

import java.util.Date;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Property;

@Entity(value="user")
public class TranslationTestClass extends BaseClass{

    @Property(value="user_name")
    private String name;
    
    @Property(value="uid")
    private int userId;
    

    
    @Property(value="pwd")
    private String password;
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getUserId() {
        return userId;
    }
    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    
}
