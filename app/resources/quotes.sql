create table if not exists quote
(
    id                 serial primary key,
    creation_timestamp timestamp,
    last_modified      timestamp,
    language           varchar(10),
    wiki               varchar(15),
    title              varchar(100) unique
);

create table if not exists quote_category
(
    id      serial primary key,
    caption varchar not null unique
);

create table if not exists quote_auxiliary_text
(
    quote_id int references quote (id),
    caption  varchar not null,
    primary key (quote_id, caption)
);

create table if not exists quote_category_relation
(
    category_id int references quote_category (id),
    quote_id    int references quote (id),
    primary key (category_id, quote_id)
)
