package com.system_design.ATF.controllers;

import com.system_design.ATF.dtos.GateRequest;
import com.system_design.ATF.dtos.RegisterLambdaRequest;
import com.system_design.ATF.entity.Lambda;
import com.system_design.ATF.services.LambdaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/lambdas")
@RequiredArgsConstructor
public class LambdaController {
    private final LambdaService lambdaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) //201
    public Lambda registerLambda(@Valid @RequestBody RegisterLambdaRequest request){
        return lambdaService.register(request);
    }

    @GetMapping
    public Page<Lambda> listLambdas(Pageable pageable){
        return lambdaService.list(pageable);
    }

    @GetMapping("/{name}")
    public Lambda getLambda(@PathVariable String name){
        return lambdaService.get(name);
    }

    @PostMapping("/{name}/gate")
    public Lambda gateLambda(@PathVariable String name, @Valid @RequestBody GateRequest request){
        return lambdaService.gate(name, request.getGateAction());
    }

    @PostMapping("/{name}/ungate")
    public Lambda ungateLambda(@PathVariable String name){
        return lambdaService.ungate(name);
    }
}
