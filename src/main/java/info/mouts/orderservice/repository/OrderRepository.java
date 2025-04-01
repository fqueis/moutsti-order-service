package info.mouts.orderservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import info.mouts.orderservice.domain.Order;

/**
 * Repository interface for managing {@link Order} entities.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    /**
     * Finds an order by its idempotency key.
     * 
     * @param idempotencyKey the idempotency key to search for
     * @return an optional containing the order if found, or empty if not found
     */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
