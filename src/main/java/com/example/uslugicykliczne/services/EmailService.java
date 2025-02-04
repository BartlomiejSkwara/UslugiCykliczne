package com.example.uslugicykliczne.services;

import com.example.uslugicykliczne.entity.ContactDataEntity;
import com.example.uslugicykliczne.entity.EmailEntity;
import com.example.uslugicykliczne.repo.EmailRepo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class EmailService {
    private final EmailRepo emailRepo;

    public EmailService(EmailRepo emailRepo) {
        this.emailRepo = emailRepo;
    }

    public List<EmailEntity> insertNewEmailEntities(List<String> emails, ContactDataEntity contactDataEntity){
        ArrayList<EmailEntity> emailEntities = new ArrayList<>();
        for(String email:emails){
            EmailEntity emailEntity = new EmailEntity();
            emailEntity.setEmail(email);
            emailEntity.setContactDataEntity(contactDataEntity);
            emailEntities.add(emailEntity);
        }
        return  emailRepo.saveAll(emailEntities);

    }

    public void updateEmailEntities(List<EmailEntity> emailEntities, List<String> emails,ContactDataEntity contactDataEntity) {

        int smallerSize  = emailEntities.size();
        if(emails.size()<emailEntities.size()){
            List<EmailEntity> entities = new ArrayList<>(emailEntities.subList(emails.size(), emailEntities.size()));
            contactDataEntity.removeEmails(entities);
            emailRepo.deleteAll(entities);
            smallerSize = emails.size();
        }
        else if (emails.size()>emailEntities.size()){
            insertNewEmailEntities(emails.subList(emailEntities.size(), emails.size()),contactDataEntity);
            smallerSize = emailEntities.size();
        }

        for (int i = 0; i<smallerSize; i++){
            emailEntities.get(i).setEmail(emails.get(i));
        }

        emailRepo.saveAll(emailEntities.subList(0,smallerSize));

    }
}
