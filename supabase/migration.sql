-- ============================================================
-- Бэклог: общий список фильмов/сериалов/аниме
-- Выполнить целиком в Supabase SQL Editor
-- ============================================================

-- Таблицы ----------------------------------------------------

create table if not exists public.lists (
    id          uuid primary key default gen_random_uuid(),
    name        text not null,
    invite_code text not null unique,
    created_by  uuid not null references auth.users (id) on delete cascade,
    created_at  timestamptz not null default now()
);

create table if not exists public.members (
    list_id      uuid not null references public.lists (id) on delete cascade,
    user_id      uuid not null references auth.users (id) on delete cascade,
    display_name text not null,
    joined_at    timestamptz not null default now(),
    primary key (list_id, user_id)
);

create table if not exists public.items (
    id          uuid primary key default gen_random_uuid(),
    list_id     uuid not null references public.lists (id) on delete cascade,
    source      text not null check (source in ('kinopoisk', 'tmdb', 'jikan', 'shikimori', 'kitsu')),
    external_id text not null,
    type        text not null check (type in ('MOVIE', 'TV', 'ANIME')),
    title       text not null,
    poster_url  text,
    synopsis    text,
    year        int,
    director    text,
    cast_list   text,          -- JSON-массив строк
    added_by    uuid not null references auth.users (id) on delete cascade,
    added_at    timestamptz not null default now(),
    unique (list_id, source, external_id)
);

create table if not exists public.item_status (
    item_id    uuid not null references public.items (id) on delete cascade,
    list_id    uuid not null references public.lists (id) on delete cascade,
    user_id    uuid not null references auth.users (id) on delete cascade,
    status     text not null check (status in ('WANT', 'WATCHING', 'WATCHED', 'DROPPED')),
    updated_at timestamptz not null default now(),
    primary key (item_id, user_id)
);

create table if not exists public.item_ratings (
    item_id    uuid not null references public.items (id) on delete cascade,
    list_id    uuid not null references public.lists (id) on delete cascade,
    user_id    uuid not null references auth.users (id) on delete cascade,
    score      int not null check (score between 1 and 10),
    updated_at timestamptz not null default now(),
    primary key (item_id, user_id)
);

create index if not exists idx_items_list on public.items (list_id, added_at desc);
create index if not exists idx_status_list on public.item_status (list_id);
create index if not exists idx_ratings_list on public.item_ratings (list_id);

-- Вспомогательная функция: участник списка? ------------------

create or replace function public.is_list_member(p_list_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.members
        where list_id = p_list_id and user_id = auth.uid()
    );
$$;

-- RPC: создание списка ----------------------------------------

create or replace function public.create_list(
    p_name         text,
    p_display_name text
)
returns public.lists
language plpgsql
security definer
set search_path = public
as $$
declare
    v_row public.lists;
    v_code text;
begin
    if auth.uid() is null then
        raise exception 'Не авторизован';
    end if;

    -- генерируем код приглашения без похожих символов
    loop
        v_code := upper(
            substr(
                translate(
                    md5(random()::text || clock_timestamp()::text),
                    '0o1il5s', 'abcdef'
                ),
                1, 6
            )
        );
        exit when not exists (select 1 from public.lists where invite_code = v_code);
    end loop;

    insert into public.lists (name, invite_code, created_by)
    values (p_name, v_code, auth.uid())
    returning * into v_row;

    insert into public.members (list_id, user_id, display_name)
    values (v_row.id, auth.uid(), p_display_name);

    return v_row;
end;
$$;

-- RPC: присоединение по коду приглашения ---------------------

create or replace function public.join_list(
    p_code         text,
    p_display_name text
)
returns public.lists
language plpgsql
security definer
set search_path = public
as $$
declare
    v_row public.lists;
begin
    if auth.uid() is null then
        raise exception 'Не авторизован';
    end if;

    select * into v_row
    from public.lists
    where invite_code = upper(p_code);

    if v_row.id is null then
        raise exception 'Код приглашения не найден';
    end if;

    insert into public.members (list_id, user_id, display_name)
    values (v_row.id, auth.uid(), p_display_name)
    on conflict (list_id, user_id)
    do update set display_name = excluded.display_name;

    return v_row;
end;
$$;

-- RLS ---------------------------------------------------------

alter table public.lists        enable row level security;
alter table public.members     enable row level security;
alter table public.items       enable row level security;
alter table public.item_status enable row level security;
alter table public.item_ratings enable row level security;

-- lists: видно только участникам
create policy lists_select on public.lists
    for select to authenticated
    using (public.is_list_member(id));

-- members: видно участников своего списка
create policy members_select on public.members
    for select to authenticated
    using (public.is_list_member(list_id));

-- members: можно переименовать себя
create policy members_update_self on public.members
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- items: читают/добавляют/удаляют участники списка
create policy items_select on public.items
    for select to authenticated
    using (public.is_list_member(list_id));

create policy items_insert on public.items
    for insert to authenticated
    with check (public.is_list_member(list_id) and added_by = auth.uid());

create policy items_delete on public.items
    for delete to authenticated
    using (public.is_list_member(list_id));

-- item_status: только свои записи
create policy status_select on public.item_status
    for select to authenticated
    using (public.is_list_member(list_id));

create policy status_write on public.item_status
    for all to authenticated
    using (user_id = auth.uid() and public.is_list_member(list_id))
    with check (user_id = auth.uid() and public.is_list_member(list_id));

-- item_ratings: только свои записи
create policy ratings_select on public.item_ratings
    for select to authenticated
    using (public.is_list_member(list_id));

create policy ratings_write on public.item_ratings
    for all to authenticated
    using (user_id = auth.uid() and public.is_list_member(list_id))
    with check (user_id = auth.uid() and public.is_list_member(list_id));

-- Realtime ---------------------------------------------------

alter publication supabase_realtime add table public.items;
alter publication supabase_realtime add table public.item_status;
alter publication supabase_realtime add table public.item_ratings;
alter publication supabase_realtime add table public.members;
