1. Analyze level of normalization of the database.
2. Analyze the queries that are being executed on the database and optimize them if necessary. Use
    EXPLAIN or similar tools to identify bottlenecks and optimize query performance.
Add big amount of data to db with help of grafana k6
3. Implement indexing on frequently queried columns to improve query performance (try to use different types
    of indexes, such as B-tree, hash, or full-text indexes, depending on the use case).
4. Try to implement partitioning of large tables to improve query performance and manageability.
5. Implement caching mechanisms to reduce the load on the database and improve response times for frequently accessed data.
6. Monitor database performance and set up alerts for any performance issues or anomalies.
7. Try to use horizontal scaling techniques, such as sharding or replication, to distribute the load across multiple 
   database instances and improve performance and availability.
8. Use transactional strategies