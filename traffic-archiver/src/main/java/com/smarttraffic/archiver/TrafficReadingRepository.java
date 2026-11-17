package com.smarttraffic.archiver;

import org.springframework.data.jpa.repository.JpaRepository;

interface TrafficReadingRepository extends JpaRepository<TrafficReadingEntity, Long> {}
