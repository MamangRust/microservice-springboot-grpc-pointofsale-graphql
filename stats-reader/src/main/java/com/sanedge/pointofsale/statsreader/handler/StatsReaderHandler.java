package com.sanedge.pointofsale.statsreader.handler;

import java.util.List;

import org.springframework.stereotype.Service;

import com.sanedge.pointofsale.common.grpc.GrpcErrorMapper;
import com.sanedge.pointofsale.common.grpc.ProtoMappers;
import com.sanedge.pointofsale.pb.stats.StatItem;
import com.sanedge.pointofsale.pb.stats.StatsReaderServiceGrpc;
import com.sanedge.pointofsale.pb.stats.StatsRequest;
import com.sanedge.pointofsale.pb.stats.StatsResponse;
import com.sanedge.pointofsale.statsreader.repository.StatsClickHouseRepository;
import com.sanedge.pointofsale.statsreader.repository.StatsClickHouseRepository.StatRow;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StatsReaderHandler extends StatsReaderServiceGrpc.StatsReaderServiceImplBase {

    private final StatsClickHouseRepository repository;

    @Override
    public void findMonthlyTotalRevenue(StatsRequest request, StreamObserver<StatsResponse> observer) {
        try {
            List<StatRow> rows = repository.monthlyTotalRevenue();
            observer.onNext(build(rows));
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(GrpcErrorMapper.toStatusRuntimeException(e));
        }
    }

    @Override
    public void findYearlyTotalRevenue(StatsRequest request, StreamObserver<StatsResponse> observer) {
        try {
            observer.onNext(build(repository.yearlyTotalRevenue()));
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(GrpcErrorMapper.toStatusRuntimeException(e));
        }
    }

    @Override
    public void findMerchantSales(StatsRequest request, StreamObserver<StatsResponse> observer) {
        try {
            observer.onNext(build(repository.merchantSales()));
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(GrpcErrorMapper.toStatusRuntimeException(e));
        }
    }

    @Override
    public void findProductSales(StatsRequest request, StreamObserver<StatsResponse> observer) {
        try {
            observer.onNext(build(repository.productSales()));
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(GrpcErrorMapper.toStatusRuntimeException(e));
        }
    }

    private StatsResponse build(List<StatRow> rows) {
        StatsResponse.Builder builder = StatsResponse.newBuilder()
                .setStatus(ProtoMappers.nvl("success"))
                .setMessage(ProtoMappers.nvl("OK"));
        for (StatRow r : rows) {
            builder.addData(StatItem.newBuilder()
                    .setPeriod(ProtoMappers.nvl(r.period()))
                    .setDimensionId(r.dimensionId())
                    .setValue(r.value())
                    .build());
        }
        return builder.build();
    }
}
