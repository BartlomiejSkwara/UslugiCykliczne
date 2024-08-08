package com.example.uslugicykliczne.services;

import com.example.uslugicykliczne.dataTypes.CyclicalServiceDto;
import com.example.uslugicykliczne.dataTypes.projections.CyclicalServiceProjection;
import com.example.uslugicykliczne.dataTypes.ServiceRenewalRecord;
import com.example.uslugicykliczne.dataTypes.StatusEnum;
import com.example.uslugicykliczne.dataTypes.projections.StatusChangeRecordProjection;
import com.example.uslugicykliczne.entity.BusinessEntity;
import com.example.uslugicykliczne.entity.CertificateEntity;
import com.example.uslugicykliczne.entity.CyclicalServiceEntity;
import com.example.uslugicykliczne.entity.ServiceUserEntity;
import com.example.uslugicykliczne.repo.*;
import com.example.uslugicykliczne.security.CustomUserDetails;
import com.example.uslugicykliczne.utility.StatusUtility;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CyclicalServiceService {

    private final CyclicalServiceRepo cyclicalServiceRepo;
    private final ServiceUserRepo serviceUserRepo;
    private final BusinessRepo businessRepo;
    private final CertificateService certificateService;
    private final EntityManager entityManager;
    private final CertificateRepo certificateRepo;
    private final SchedulingService schedulingService;
    private final ServiceStatusHistoryService serviceStatusHistoryService;
    private final StatusChangeRepo statusChangeRepo;


    public ResponseEntity<String> renewCyclicalService(ServiceRenewalRecord serviceRenewalRecord, Integer serviceId){
        Optional<CertificateEntity> certificateEntityOptional = certificateRepo.findCertificateWithNotRenewedCertBy(serviceId);
        if(certificateEntityOptional.isEmpty())
            return ResponseEntity.badRequest().body("Can't renew nonexistent service");
        CertificateEntity certificateEntity = certificateEntityOptional.get();

        if(certificateEntity.getCyclicalServiceEntity().isOneTime())
            return ResponseEntity.badRequest().body("Can't renew one time service");


        if(certificateEntity.isRenewalMessageSent()){
            schedulingService.trySchedulingReminderWhenInserted(certificateEntity,certificateEntity.getCyclicalServiceEntity());

        } else {
            schedulingService.trySchedulingReminderWhenUpdated(certificateEntity,certificateEntity.getCyclicalServiceEntity());
        }
        certificateEntity.setRenewed(true);
        certificateEntity.setRenewalMessageSent(true);

        CyclicalServiceEntity cyclicalService = certificateEntity.getCyclicalServiceEntity();

        changeServiceStatus(cyclicalService, StatusEnum.RENEWED.getMaskValue());
        serviceStatusHistoryService.addNewStatusHistoryRecord(null,StatusEnum.RENEWED,"Usługa została odnowiona",serviceId);
        certificateService.insertCertificateCreatedFromRenewalRecord(cyclicalService, serviceRenewalRecord);

        return ResponseEntity.ok().body("The task was successfully renewed");
    }

    public ResponseEntity<String> insertNewCyclicalServiceEntity(CyclicalServiceDto cyclicalServiceDto){
        Optional<ServiceUserEntity> serviceUserEntityOptional = serviceUserRepo.findById(cyclicalServiceDto.getServiceUserId());
        Optional<BusinessEntity> businessEntityOptional = businessRepo.findById(cyclicalServiceDto.getBusinessId());

        if(businessEntityOptional.isPresent() && serviceUserEntityOptional.isPresent()){
            CyclicalServiceEntity insertedEntity = cyclicalServiceRepo.save(createCyclicalServiceDTOFromEntity(new CyclicalServiceEntity(),cyclicalServiceDto,serviceUserEntityOptional.get(),businessEntityOptional.get()));
            CertificateEntity certificateEntity = certificateService.insertCertificateCreatedFromCyclicalServiceDTO(insertedEntity,cyclicalServiceDto);
            //cyclicalServiceEntity.setCertificates(dto.getRenewalPeriod());
            //schedulingService.trySchedulingReminderWhenInserted(insertedEntity);
            if(certificateEntity!=null){
                schedulingService.trySchedulingReminderWhenInserted(certificateEntity,insertedEntity);
                return ResponseEntity.ok("Successfully added the cyclical service");

            }
            return  ResponseEntity.internalServerError().body("Couldn't create certificate");
        }

        StringBuilder stringBuilder = new StringBuilder("Entities :");
        stringBuilder.append((serviceUserEntityOptional.isEmpty())?"[service user]":"");
        stringBuilder.append((businessEntityOptional.isEmpty())?"[business]":"");
        stringBuilder.append("with the provided ID(s) could not be found.");
        return  ResponseEntity.badRequest().body(stringBuilder.toString());
    }

    private CyclicalServiceEntity createCyclicalServiceDTOFromEntity(CyclicalServiceEntity cyclicalServiceEntity, CyclicalServiceDto dto, ServiceUserEntity serviceUserEntity, BusinessEntity businessEntity){
        cyclicalServiceEntity.setServiceUser(serviceUserEntity);
        cyclicalServiceEntity.setBusiness(businessEntity);
        cyclicalServiceEntity.setDescription(dto.getDescription());
        cyclicalServiceEntity.setPrice(dto.getPrice());
        cyclicalServiceEntity.setOneTime(dto.getOneTime());
        cyclicalServiceEntity.setAgreementNumber(dto.getAgreementNumber());

        return cyclicalServiceEntity;
    }

    public List<CyclicalServiceProjection> getAllFromNextNDays(int nDays) {
        return cyclicalServiceRepo.customFindCyclicalProjectionsInNextNDays(nDays);
    }


    public ResponseEntity<String> changeServiceStatusAndUpdateDB(Integer id,Integer requestedStatusChange, String statusChangeComment) {
        Optional<CyclicalServiceEntity> optionalCyclicalServiceEntity = cyclicalServiceRepo.findById(id);
        if(optionalCyclicalServiceEntity.isEmpty())
            return ResponseEntity.badRequest().body("Nie można zmienić statusu nie istniejącej usługi");

        CyclicalServiceEntity cyclicalService = optionalCyclicalServiceEntity.get();
        changeServiceStatus(cyclicalService,requestedStatusChange);

        serviceStatusHistoryService.addNewStatusHistoryRecord(null,requestedStatusChange,statusChangeComment,cyclicalService.getIdCyclicalService());
        cyclicalServiceRepo.save(cyclicalService);
        return ResponseEntity.ok("Zmiana statusu dokonana z powodzeniem");
    }

    public void changeServiceStatus(CyclicalServiceEntity cyclicalService,Integer requestedStatusChange) {
        int statusBitmap = cyclicalService.getStatusBitmap();
        if(StatusUtility.hasStatus(statusBitmap,StatusEnum.RENEWED)){
            statusBitmap = 0;
        }


        if (requestedStatusChange.equals(StatusEnum.RENEWED.getMaskValue())||
                requestedStatusChange.equals(StatusEnum.AWAITING_RENEWAL.getMaskValue())||
                requestedStatusChange.equals(StatusEnum.CANCELED.getMaskValue())||
                requestedStatusChange.equals(StatusEnum.RENEWED_ELSEWHERE.getMaskValue()))
            statusBitmap = requestedStatusChange;
        else
            statusBitmap = StatusUtility.addStatus(statusBitmap,requestedStatusChange);

        cyclicalService.setStatusBitmap(statusBitmap);
    }


    public ResponseEntity<String> cancelRequest(Integer serviceId,String statusChangeComment) {

        Optional<CyclicalServiceEntity> optionalCyclicalServiceEntity = cyclicalServiceRepo.findCyclicalServiceAcDataJoin(serviceId);
        if(optionalCyclicalServiceEntity.isEmpty())
            return ResponseEntity.badRequest().body("Can't renew nonexistent service");

        CyclicalServiceEntity cyclicalService = optionalCyclicalServiceEntity.get();


        if(!SecurityContextHolder.getContext().getAuthentication().getName().equals(cyclicalService.getAssignedAccountDataEntity().getUsername()))
            return new ResponseEntity<>("Don't modify resources you don't own !!!", HttpStatus.FORBIDDEN);

        changeServiceStatus(cyclicalService,StatusEnum.MARKED_FOR_CANCEL.getMaskValue());

        serviceStatusHistoryService.addNewStatusHistoryRecord(null,StatusEnum.MARKED_FOR_CANCEL.getMaskValue(),statusChangeComment,cyclicalService.getIdCyclicalService());
        cyclicalServiceRepo.save(cyclicalService);

        return ResponseEntity.ok("Successfully requested service cancellation");
    }

    public ResponseEntity<String> requestRenewal(Integer serviceId, String statusChangeComment){
        Optional<CyclicalServiceEntity> optionalCyclicalServiceEntity = cyclicalServiceRepo.customFindNameOfAccountAssignedToService(serviceId);
        if(optionalCyclicalServiceEntity.isEmpty())
            return ResponseEntity.badRequest().body("Can't renew nonexistent service");
        CyclicalServiceEntity cyclicalService = optionalCyclicalServiceEntity.get();

        if(!SecurityContextHolder.getContext().getAuthentication().getName().equals(cyclicalService.getAssignedAccountDataEntity().getUsername()))
            return new ResponseEntity<>("Don't modify resources you don't own !!!", HttpStatus.FORBIDDEN);


        if (cyclicalService.getStatusBitmap()!=StatusEnum.RENEWED.getMaskValue()){
            return ResponseEntity.badRequest().body("You can only request renewal of service that has finished last renewal process");
        }
        changeServiceStatus(cyclicalService,StatusEnum.AWAITING_RENEWAL.getMaskValue());
        serviceStatusHistoryService.addNewStatusHistoryRecord(null,StatusEnum.AWAITING_RENEWAL.getMaskValue(),statusChangeComment,cyclicalService.getIdCyclicalService());
        cyclicalServiceRepo.save(cyclicalService);

        return ResponseEntity.ok("Successfully requested renewal");
    }

    public List<StatusChangeRecordProjection> getStatusChangesRelatedToService(Integer serviceId) {

        Optional<CyclicalServiceEntity> optionalCyclicalServiceEntity = cyclicalServiceRepo.findCyclicalServiceAcDataJoin(serviceId);
        if (optionalCyclicalServiceEntity.isEmpty())
            return List.of();

        CyclicalServiceEntity cyclicalService = optionalCyclicalServiceEntity.get();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = ((CustomUserDetails)authentication.getPrincipal());

        if (!userDetails.getRole().equals("ROLE_admin")&&!userDetails.getRole().equals("ROLE_editor")){
            if(!authentication.getName().equals(cyclicalService.getAssignedAccountDataEntity().getUsername()))
                return List.of();
        }


        return statusChangeRepo.findByServiceIdWithChronologicalOrder(serviceId);
    }

//    public ResponseEntity<String> updateCyclicalServiceEntity(Integer id, CyclicalServiceDto cyclicalServiceDto){
//        Optional<CyclicalServiceEntity> cyclicalServiceEntity = cyclicalServiceRepo.findById(id);
//        if (cyclicalServiceEntity.isEmpty())
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Can't edit nonexistent cyclical service !!!");
//
//
//        Optional<CustomerEntity> customerEntity = customerRepo.findById(cyclicalServiceDto.getCustomerId());
//        Optional<DysponentEntity> dysponentEntity = dysponentRepo.findById(cyclicalServiceDto.getDysponentId());
//
//        if(customerEntity.isPresent() && dysponentEntity.isPresent()){
//            CyclicalServiceEntity cyclicalServiceEntity1 = cyclicalServiceRepo.save(convertCyclicalServiceDTOtoEntity(cyclicalServiceEntity.get(),cyclicalServiceDto,customerEntity.get(),dysponentEntity.get()));
//            schedulingService.trySchedulingReminderWhenUpdated(cyclicalServiceEntity1);
//            return ResponseEntity.ok("Successfully updated the cyclical service");
//        }
//
//        StringBuilder stringBuilder = new StringBuilder("Entities :");
//        stringBuilder.append((customerEntity.isEmpty())?"[customer]":"");
//        stringBuilder.append((dysponentEntity.isEmpty())?"[dysponent]":"");
//        stringBuilder.append("with the provided ID(s) could not be found.");
//        return  ResponseEntity.badRequest().body(stringBuilder.toString());
//
//
//
//    }
//
//
}
