package com.example.uslugicykliczne.services;

import com.example.uslugicykliczne.dataTypes.BusinessDTO;
import com.example.uslugicykliczne.entity.BusinessEntity;
import com.example.uslugicykliczne.entity.ContactDataEntity;
import com.example.uslugicykliczne.repo.BusinessRepo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class BusinessService {
    private final BusinessRepo businessRepo;
    private final ContactDataService contactDataService;

    public BusinessService(BusinessRepo businessRepo, ContactDataService contactDataService) {
        this.businessRepo = businessRepo;
        this.contactDataService = contactDataService;
    }



    public ResponseEntity<String> updateBusinessEntity(Integer id,BusinessDTO businessDTO){
        Optional<BusinessEntity> businessEntityOptional = businessRepo.findBusinessWithContactDataById(id);
        if (businessEntityOptional.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Can't edit nonexistent business !!!");

        ContactDataEntity contactDataEntity = businessEntityOptional.get().getContactData();
        contactDataService.updateContactDataEntity(contactDataEntity,businessDTO.getEmails(),businessDTO.getPhoneNumbers());

        businessRepo.save(createBusinessEntityFromDTO(businessEntityOptional.get(), businessDTO, contactDataEntity));
        return ResponseEntity.ok("Successfully updated the business");
    }

    public ResponseEntity<String> insertNewBusinessEntity(BusinessDTO businessDTO){
//        List<BusinessEntity> duplicateUniques = dysponentRepo.findDysponentEntitiesByEmailOrPhoneNumberOrMfnSerialNumber(dysponentDto.getEmail(), dysponentDto.getPhoneNumber(), dysponentDto.getMfnSerialNumber());
//        if(duplicateUniques.isEmpty()){
        ContactDataEntity contactDataEntity = contactDataService.insertContactDataEntity(businessDTO.getEmails(),businessDTO.getPhoneNumbers());
        businessRepo.save(createBusinessEntityFromDTO(new BusinessEntity() ,businessDTO,contactDataEntity));
        return ResponseEntity.ok("Successfully added the user");
//        }else {
//            StringBuilder error = new StringBuilder("There can't be Dysponents with duplicate:");
//
//            HashSet<String> duplicates = new HashSet<>();
//            duplicateUniques.forEach(dysponentEntity ->
//                    {
//                        if(dysponentEntity.getEmail().equals(dysponentDto.getEmail()))
//                            duplicates.add("[email]");
//                        if(dysponentEntity.getPhoneNumber().equals(dysponentDto.getPhoneNumber()))
//                            duplicates.add("[phone number]");
//                        if(dysponentEntity.getMfnSerialNumber().equals(dysponentDto.getMfnSerialNumber()))
//                            duplicates.add("[mfn serial number]");
//                    }
//
//            );
//
//            duplicates.forEach(s ->
//                    error.append(s)
//            );

            //return ResponseEntity.status(409).body("");//error.toString());
        //}
    }
    public BusinessEntity createBusinessEntityFromDTO(BusinessEntity businessEntity, BusinessDTO dto, ContactDataEntity contactDataEntity ){
        businessEntity.setName(dto.getName());
        businessEntity.setComments(dto.getComments());
        businessEntity.setContactData(contactDataEntity);
        businessEntity.setAdres(dto.getAdres());
        businessEntity.setNip(dto.getNip());
        businessEntity.setRegon(dto.getRegon());
        return businessEntity;
    }

    public void deleteBusiness(Integer id) {
        Optional<BusinessEntity> businessEntity = businessRepo.findById(id);
        if (businessEntity.isPresent()){
            businessRepo.delete(businessEntity.get());
        }

    }
}
