CREATE OR REPLACE FUNCTION de_metas_contracts.fetchflatratetermhierarchy_byC_Flatrate_Term_id(IN p_c_flatrate_term_id numeric)
  RETURNS TABLE(bill_bpartner_id numeric, initial_ft_id numeric, path numeric[]) AS
$BODY$
 WITH RECURSIVE node_graph AS (
   SELECT ft.bill_bpartner_id, ft.c_flatrate_term_id as initial_ft_id, ft.c_flatrateterm_next_id as next_ft_id, ARRAY[ft.c_flatrate_term_id, ft.c_flatrateterm_next_id] AS path
   FROM   c_flatrate_term ft 
     JOIN getInitialC_Flatrate_term_ID(p_c_flatrate_term_id) as parent on parent.c_flatrate_term_id = ft.c_flatrate_term_id 

   UNION  ALL

    SELECT ng.bill_bpartner_id, ng.initial_ft_id, ft3.c_flatrateterm_next_id as next_ft_id,
           (ng.path || ft3.c_flatrateterm_next_id )::numeric(10,0)[] as path
    FROM node_graph ng
    JOIN c_flatrate_term ft3 ON ng.next_ft_id = ft3.c_flatrate_term_id
   )
SELECT *
from
    (
        SELECT n1.bill_bpartner_id, n1.initial_ft_id, n1.path
        from node_graph AS n1
        where true
        and not exists (select 1 from node_graph as n2 where n1.path <@ n2.path)
    ) as data
where true
and (select count(*) from node_graph as n3 where data.initial_ft_ID = any(n3.path) 
        and data.bill_bpartner_id=n3.bill_bpartner_id 
        and not exists (select 1 from node_graph as n4 where n3.path <@ n4.path) ) = 1
order by initial_ft_id;
$BODY$
  LANGUAGE sql STABLE
  COST 100
  ROWS 1000;