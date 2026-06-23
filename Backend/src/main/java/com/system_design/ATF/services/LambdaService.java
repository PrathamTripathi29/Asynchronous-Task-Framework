package com.system_design.ATF.services;

import com.system_design.ATF.dtos.RegisterLambdaRequest;
import com.system_design.ATF.entity.GateAction;
import com.system_design.ATF.entity.Lambda;
import com.system_design.ATF.entity.LambdaStatus;
import com.system_design.ATF.exception.DuplicateLambdaException;
import com.system_design.ATF.exception.LambdaNotFoundException;
import com.system_design.ATF.repository.LambdaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LambdaService {
    private final LambdaRepository lambdaRepository;
    private final ConsumerGroupInitializer consumerGroupInitializer;

    public Lambda get(String lambdaName){
        return lambdaRepository.findByName(lambdaName)
                .orElseThrow(() -> new LambdaNotFoundException("Lambda " + lambdaName + " not found."));
    }

    public Page<Lambda> list(Pageable pageable){
        return lambdaRepository.findAll(pageable);
    }

    //register a new lambda
    public Lambda register(RegisterLambdaRequest request){
        if(lambdaRepository.existsByName(request.getName())){
            throw new DuplicateLambdaException("Lambda with name " + request.getName() + " already exists");
        }

        Lambda newLambda = Lambda.builder()
                .name(request.getName())
                .description(request.getDescription())
                .callbackUrl(request.getCallbackUrl())
                .ownerTeam(request.getOwnerTeam())
                .build();

        Lambda savedLambda = lambdaRepository.save(newLambda);

        consumerGroupInitializer.initializeGroup(savedLambda.getName());
        return savedLambda;
    }

    public Lambda gate(String lambdaName, GateAction action){
        Lambda lambda = get(lambdaName);
        lambda.setStatus(LambdaStatus.PAUSED);
        lambda.setGateAction(action);
        return lambdaRepository.save(lambda);
    }

    public Lambda ungate(String lambdaName){
        Lambda lambda = get(lambdaName);
        lambda.setStatus(LambdaStatus.ACTIVE);
        lambda.setGateAction(null);
        return lambdaRepository.save(lambda);
    }
}
