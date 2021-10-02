-- src/backend/pacients.sql

-- :name create-pacients-table :!
create table if not exists pacients (
  id                serial primary key,
  second_name       varchar(40),
  first_name        varchar(40),
  third_name        varchar(40),
  sex               char(1),
  dob               DATE,
  address           varchar(40),
  oms               BIGINT
  )

-- :name insert-pacient :<!
insert into pacients (
  second_name,
  first_name,
  third_name,
  sex,
  dob,
  address,
  oms
  )
values (
  :second-name,
  :first-name,
  :third-name,
  :sex,
  :dob,
  :address,
  :oms
  )
returning id

-- :name update-pacient :! :n
update pacients
set
  second_name = :second-name,
  first_name = :first-name,
  third_name = :third-name,
  sex = :sex,
  dob = :dob,
  address = :address,
  oms = :oms
where id = :id

-- :name get-user-by-oms :? :1
select * from pacients where oms = :oms

-- :name get-user-by-id :? :1
select * from pacients where id = :id

-- :name all-pacients :? :*
select * from pacients order by id desc

-- :name remove-pacient-by-id :! :n
delete from pacients where id = :id

-- :name drop-pacients-table :! :n
drop table if exists pacients
